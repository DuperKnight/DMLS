package com.duperknight.client.feedback;

import com.duperknight.DMLS;
import com.duperknight.client.utils.DMLSFunctionApi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Multipart client for the public DMLS feedback endpoint. */
public final class FeedbackService {
    public static final long MAX_SCREENSHOT_BYTES = 5L * 1024L * 1024L;
    public static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;
    public static final long MAX_REQUEST_BYTES = 8L * 1024L * 1024L;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private FeedbackService() {
    }

    public static CompletableFuture<Result> submit(Submission submission) {
        return CompletableFuture.supplyAsync(() -> submitBlocking(submission));
    }

    private static Result submitBlocking(Submission submission) {
        try {
            Multipart multipart = createMultipart(submission);
            HttpRequest request = DMLSFunctionApi.request("/v1/feedback", REQUEST_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + multipart.boundary())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.body()))
                    .build();
            return parseResponse(HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (SubmissionException exception) {
            return Result.failure(exception.status, exception.getMessage());
        } catch (IOException exception) {
            DMLS.LOGGER.warn("Could not read feedback files or reach the feedback service", exception);
            return Result.failure(Status.NETWORK_ERROR,
                    "Could not read the selected files or reach the feedback service.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failure(Status.NETWORK_ERROR, "The feedback submission was interrupted.");
        } catch (RuntimeException exception) {
            DMLS.LOGGER.warn("Could not submit feedback", exception);
            return Result.failure(Status.NETWORK_ERROR,
                    "Could not reach the feedback service. Check your connection and try again.");
        }
    }

    public static List<Path> diagnosticLogs() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        List<Path> logs = new ArrayList<>();
        Path logDirectory = gameDir.resolve("logs");
        addIfEligible(logs, logDirectory.resolve("latest.log"));
        addIfEligible(logs, logDirectory.resolve("debug.log"));

        if (Files.isDirectory(logDirectory)) {
            try (var entries = Files.list(logDirectory)) {
                entries.filter(Files::isRegularFile)
                        .filter(FeedbackService::isArchivedLog)
                        .max(Comparator.comparingLong(FeedbackService::lastModified))
                        .ifPresent(path -> addIfEligible(logs, path));
            } catch (IOException exception) {
                DMLS.LOGGER.debug("Could not inspect the archived logs directory", exception);
            }
        }
        return List.copyOf(logs.subList(0, Math.min(3, logs.size())));
    }

    public static String minecraftVersion() {
        return versionOf("minecraft");
    }

    public static String modVersion() {
        return versionOf(DMLS.MOD_ID);
    }

    public static String loaderVersion() {
        return "Fabric Loader " + versionOf("fabricloader");
    }

    public static String operatingSystem() {
        String name = System.getProperty("os.name", "unknown");
        String version = System.getProperty("os.version", "");
        return (name + (version.isBlank() ? "" : " " + version)).strip();
    }

    private static Multipart createMultipart(Submission submission) throws IOException, SubmissionException {
        String title = submission.title().strip();
        String description = submission.description().strip();
        if (title.isEmpty()) throw new SubmissionException(Status.INVALID_INPUT, "Enter a feedback title.");
        if (title.length() > 120) throw new SubmissionException(Status.INVALID_INPUT,
                "The feedback title must be 120 characters or fewer.");
        if (description.isEmpty()) throw new SubmissionException(Status.INVALID_INPUT,
                "Enter a description.");
        if (description.length() > 2_000) throw new SubmissionException(Status.INVALID_INPUT,
                "The description must be 2,000 characters or fewer.");

        MultipartBuilder builder = new MultipartBuilder();
        builder.text("title", title);
        builder.text("category", submission.category());
        builder.text("description", description);
        if (submission.includeDiagnostics()) {
            builder.text("minecraftVersion", minecraftVersion());
            builder.text("modVersion", modVersion());
            builder.text("loaderVersion", loaderVersion());
            builder.text("operatingSystem", operatingSystem());
        }

        if (submission.screenshot() != null) {
            Path screenshot = submission.screenshot();
            long size = Files.size(screenshot);
            if (size <= 0) throw new SubmissionException(Status.FILE_ERROR, "The selected screenshot is empty.");
            if (size > MAX_SCREENSHOT_BYTES) throw new SubmissionException(Status.PAYLOAD_TOO_LARGE,
                    "The screenshot must be 5 MiB or smaller.");
            byte[] contents = Files.readAllBytes(screenshot);
            String contentType = screenshotContentType(contents);
            if (contentType == null) throw new SubmissionException(Status.FILE_ERROR,
                    "Select a PNG, JPEG, or WebP screenshot.");
            builder.file("screenshot", screenshot.getFileName().toString(), contentType, contents);
        }

        if (submission.includeDiagnostics()) {
            for (Path log : diagnosticLogs()) {
                byte[] contents = Files.readAllBytes(log);
                if (contents.length == 0 || contents.length > MAX_LOG_BYTES) continue;
                builder.file("logs", log.getFileName().toString(), logContentType(log), contents);
            }
        }

        Multipart multipart = builder.build();
        if (multipart.body().length > MAX_REQUEST_BYTES) {
            throw new SubmissionException(Status.PAYLOAD_TOO_LARGE,
                    "The screenshot and logs exceed the 8 MiB request limit. Remove the screenshot or disable logs.");
        }
        return multipart;
    }

    private static Result parseResponse(HttpResponse<String> response) {
        JsonObject body = null;
        try {
            if (!response.body().isBlank()) body = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException ignored) {
        }
        if (response.statusCode() == 201 && body != null) {
            String feedbackId = string(body, "feedbackId");
            if (!feedbackId.isBlank()) return Result.submitted(feedbackId);
        }

        String message = body == null ? "" : string(body, "message");
        if (message.isBlank()) {
            message = switch (response.statusCode()) {
                case 400 -> "The submission was rejected. Check the fields and selected files.";
                case 413 -> "The selected files are too large.";
                case 429 -> "Too many feedback attempts. Please wait before trying again.";
                default -> "The feedback service returned an unexpected response.";
            };
        }
        Status status = switch (response.statusCode()) {
            case 400 -> Status.INVALID_INPUT;
            case 413 -> Status.PAYLOAD_TOO_LARGE;
            case 429 -> Status.RATE_LIMITED;
            default -> Status.SERVICE_ERROR;
        };
        return Result.failure(status, message);
    }

    private static String screenshotContentType(byte[] contents) {
        if (contents.length >= 8
                && (contents[0] & 0xFF) == 0x89 && contents[1] == 0x50 && contents[2] == 0x4E
                && contents[3] == 0x47 && contents[4] == 0x0D && contents[5] == 0x0A
                && contents[6] == 0x1A && contents[7] == 0x0A) return "image/png";
        if (contents.length >= 3
                && (contents[0] & 0xFF) == 0xFF && (contents[1] & 0xFF) == 0xD8
                && (contents[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (contents.length >= 12
                && contents[0] == 'R' && contents[1] == 'I' && contents[2] == 'F' && contents[3] == 'F'
                && contents[8] == 'W' && contents[9] == 'E' && contents[10] == 'B' && contents[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String logContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gz")) return "application/gzip";
        if (name.endsWith(".zip")) return "application/zip";
        return "text/plain";
    }

    private static String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static void addIfEligible(List<Path> logs, Path path) {
        try {
            if (Files.isRegularFile(path)) {
                long size = Files.size(path);
                if (size > 0 && size <= MAX_LOG_BYTES) logs.add(path);
            }
        } catch (IOException ignored) {
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean isArchivedLog(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("latest.log") || name.equals("debug.log")) return false;
        return name.endsWith(".log") || name.endsWith(".txt")
                || name.endsWith(".log.gz") || name.endsWith(".gz") || name.endsWith(".zip");
    }

    public record Submission(String title, String category, String description, Path screenshot,
                             boolean includeDiagnostics) {
    }

    public record Result(Status status, String feedbackId, String message) {
        private static Result submitted(String feedbackId) {
            return new Result(Status.SUBMITTED, feedbackId, "");
        }

        private static Result failure(Status status, String message) {
            return new Result(status, "", message);
        }

        public boolean succeeded() {
            return status == Status.SUBMITTED;
        }
    }

    public enum Status {
        SUBMITTED,
        INVALID_INPUT,
        FILE_ERROR,
        PAYLOAD_TOO_LARGE,
        RATE_LIMITED,
        NETWORK_ERROR,
        SERVICE_ERROR
    }

    record Multipart(String boundary, byte[] body) {
    }

    private static final class SubmissionException extends Exception {
        private final Status status;

        private SubmissionException(Status status, String message) {
            super(message);
            this.status = status;
        }
    }

    static final class MultipartBuilder {
        private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final Base64.Encoder FILE_ENCODER = Base64.getMimeEncoder(76, CRLF);
        private final String boundary = "DMLS-" + UUID.randomUUID();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private void text(String name, String value) throws IOException {
            header("Content-Disposition: form-data; name=\"" + name + "\"");
            output.write(CRLF);
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.write(CRLF);
        }

        void file(String name, String filename, String contentType, byte[] contents) throws IOException {
            header("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                    + safeFilename(filename) + "\"");
            output.write(("Content-Type: " + contentType).getBytes(StandardCharsets.US_ASCII));
            output.write(CRLF);
            output.write("Content-Transfer-Encoding: base64".getBytes(StandardCharsets.US_ASCII));
            output.write(CRLF);
            output.write(CRLF);
            output.write(FILE_ENCODER.encode(contents));
            output.write(CRLF);
        }

        private void header(String disposition) throws IOException {
            output.write(("--" + boundary).getBytes(StandardCharsets.US_ASCII));
            output.write(CRLF);
            output.write(disposition.getBytes(StandardCharsets.UTF_8));
            output.write(CRLF);
        }

        Multipart build() throws IOException {
            output.write(("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII));
            output.write(CRLF);
            return new Multipart(boundary, output.toByteArray());
        }

        private static String safeFilename(String filename) {
            return filename.replace("\\", "_").replace("\"", "_")
                    .replace("\r", "_").replace("\n", "_");
        }
    }
}
