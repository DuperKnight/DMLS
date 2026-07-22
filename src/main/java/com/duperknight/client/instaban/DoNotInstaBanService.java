package com.duperknight.client.instaban;

import com.duperknight.client.accountlink.DiscordLinkTokenStore;
import com.duperknight.client.accountlink.DiscordAccountProfileStore;
import com.duperknight.client.accountlink.DiscordLinkRequestManager;
import com.duperknight.client.utils.InputValidators;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Asynchronous authenticated client for the DMLS Do-Not-Insta-Ban Appwrite route. */
public final class DoNotInstaBanService {
    static final int MAX_PLAYERS_PER_REQUEST = 60;
    static final int MAX_BODY_BYTES = 2_048;
    private static final int MAX_RETRIES = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(35);
    private static final URI EXECUTION_URI = URI.create(
            "https://fra.cloud.appwrite.io/v1/functions/dmls-linking/executions");
    private static final String APPWRITE_PROJECT = "68305f510028a84a7227";
    private static final DiscordLinkRequestManager REQUEST_MANAGER = DiscordLinkRequestManager.shared();
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "DMLS Do-Not-Insta-Ban");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(IO_EXECUTOR)
            .build();
    private static final DoNotInstaBanService SHARED = new DoNotInstaBanService(
            request -> HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> new TransportResponse(response.statusCode(), response.body(),
                            response.headers().firstValue("Retry-After").orElse(""))),
            new CredentialAccess() {
                @Override public Optional<String> load(UUID uuid) {
                    Optional<String> token = DiscordLinkTokenStore.load(uuid);
                    if (token.isEmpty()) DiscordAccountProfileStore.delete(uuid);
                    return token;
                }
                @Override public void delete(UUID uuid) {
                    DiscordLinkTokenStore.delete(uuid);
                    DiscordAccountProfileStore.delete(uuid);
                }
            },
            (delayMillis, task) -> CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, IO_EXECUTOR)
                    .execute(task),
            () -> ThreadLocalRandom.current().nextLong(251),
            IO_EXECUTOR);

    private final Transport transport;
    private final CredentialAccess credentials;
    private final RetryScheduler scheduler;
    private final LongSupplier jitterMillis;
    private final Executor worker;
    private final Map<RequestKey, CompletableFuture<InstaBanLookupOutcome>> inFlight = new ConcurrentHashMap<>();

    DoNotInstaBanService(Transport transport, CredentialAccess credentials, RetryScheduler scheduler,
                         LongSupplier jitterMillis, Executor worker) {
        this.transport = transport;
        this.credentials = credentials;
        this.scheduler = scheduler;
        this.jitterMillis = jitterMillis;
        this.worker = worker;
    }

    public static DoNotInstaBanService shared() {
        return SHARED;
    }

    public CompletableFuture<InstaBanLookupOutcome> check(UUID minecraftUuid, List<String> players) {
        if (minecraftUuid == null || players == null || players.isEmpty()
                || players.stream().anyMatch(player -> !InputValidators.isUsername(player))) {
            return CompletableFuture.completedFuture(InstaBanLookupOutcome.failure(
                    InstaBanLookupOutcome.Type.BAD_REQUEST));
        }
        List<String> submitted = List.copyOf(players);
        RequestKey key = new RequestKey(minecraftUuid, submitted);
        CompletableFuture<InstaBanLookupOutcome> created = new CompletableFuture<>();
        CompletableFuture<InstaBanLookupOutcome> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) return dependent(existing);
        start(minecraftUuid, submitted).whenComplete((result, error) -> {
            if (error == null) created.complete(result);
            else created.completeExceptionally(error);
            inFlight.remove(key, created);
        });
        return dependent(created);
    }

    private CompletableFuture<InstaBanLookupOutcome> start(UUID minecraftUuid, List<String> players) {
        return CompletableFuture.supplyAsync(() -> credentials.load(minecraftUuid), worker)
                .thenComposeAsync(token -> token.<CompletableFuture<InstaBanLookupOutcome>>map(value -> {
                    List<CompletableFuture<BatchOutcome>> batches = new ArrayList<>();
                    for (int start = 0; start < players.size(); start += MAX_PLAYERS_PER_REQUEST) {
                        int end = Math.min(players.size(), start + MAX_PLAYERS_PER_REQUEST);
                        batches.add(sendBatch(minecraftUuid, value, players.subList(start, end), 0));
                    }
                    return combineBatches(batches);
                }).orElseGet(() -> CompletableFuture.completedFuture(
                        InstaBanLookupOutcome.failure(InstaBanLookupOutcome.Type.NOT_LINKED))), worker);
    }

    private CompletableFuture<BatchOutcome> sendBatch(UUID minecraftUuid, String token,
                                                       List<String> players, int retry) {
        final HttpRequest request;
        try {
            request = createRequest(token, players);
        } catch (IllegalArgumentException error) {
            return CompletableFuture.completedFuture(BatchOutcome.failure(
                    InstaBanLookupOutcome.Type.CONFIGURATION_ERROR));
        }

        AppwriteRequestKey requestKey = new AppwriteRequestKey(minecraftUuid, token, List.copyOf(players));
        return REQUEST_MANAGER.submit(requestKey, () -> transport.send(request)).handleAsync((response, error) -> {
            if (error != null) {
                return retry < MAX_RETRIES
                        ? delayedRetry(minecraftUuid, token, players, retry, exponentialDelayMillis(retry))
                        : CompletableFuture.completedFuture(BatchOutcome.failure(
                                InstaBanLookupOutcome.Type.TEMPORARY_ERROR));
            }
            return handleResponse(minecraftUuid, token, players, retry, response);
        }, worker).thenCompose(future -> future);
    }

    private CompletableFuture<BatchOutcome> handleResponse(UUID minecraftUuid, String token, List<String> players,
                                                            int retry, TransportResponse executionResponse) {
        final TransportResponse response;
        try {
            response = unwrapExecutionResponse(executionResponse);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(BatchOutcome.failure(
                    InstaBanLookupOutcome.Type.MALFORMED_RESPONSE));
        }
        String errorCode = errorCode(response.body());
        if (response.statusCode() == 200) {
            try {
                return CompletableFuture.completedFuture(BatchOutcome.success(parseSuccess(response.body(), players)));
            } catch (RuntimeException error) {
                return CompletableFuture.completedFuture(BatchOutcome.failure(
                        InstaBanLookupOutcome.Type.MALFORMED_RESPONSE));
            }
        }
        if (response.statusCode() == 401 && "invalid_token".equals(errorCode)) {
            credentials.delete(minecraftUuid);
            return CompletableFuture.completedFuture(BatchOutcome.failure(InstaBanLookupOutcome.Type.INVALID_TOKEN));
        }
        if (response.statusCode() == 403 && "authorization_stale".equals(errorCode)) {
            return CompletableFuture.completedFuture(BatchOutcome.failure(
                    InstaBanLookupOutcome.Type.AUTHORIZATION_STALE));
        }
        if (response.statusCode() == 400 && "bad_request".equals(errorCode)) {
            return CompletableFuture.completedFuture(BatchOutcome.failure(InstaBanLookupOutcome.Type.BAD_REQUEST));
        }
        if (response.statusCode() == 404 || response.statusCode() == 405) {
            return CompletableFuture.completedFuture(BatchOutcome.failure(
                    InstaBanLookupOutcome.Type.CONFIGURATION_ERROR));
        }
        if (response.statusCode() == 429) {
            if (retry >= MAX_RETRIES) {
                return CompletableFuture.completedFuture(BatchOutcome.failure(
                        InstaBanLookupOutcome.Type.RATE_LIMITED));
            }
            long delay = retryAfterMillis(response.retryAfter()) + Math.max(0, jitterMillis.getAsLong());
            return delayedRetry(minecraftUuid, token, players, retry, delay);
        }
        if (response.statusCode() == 500 || response.statusCode() == 503) {
            return retry < MAX_RETRIES
                    ? delayedRetry(minecraftUuid, token, players, retry, exponentialDelayMillis(retry))
                    : CompletableFuture.completedFuture(BatchOutcome.failure(
                            InstaBanLookupOutcome.Type.TEMPORARY_ERROR));
        }
        return CompletableFuture.completedFuture(BatchOutcome.failure(InstaBanLookupOutcome.Type.TEMPORARY_ERROR));
    }

    private CompletableFuture<BatchOutcome> delayedRetry(UUID minecraftUuid, String token, List<String> players,
                                                          int retry, long delayMillis) {
        CompletableFuture<BatchOutcome> delayed = new CompletableFuture<>();
        scheduler.schedule(Math.max(0, delayMillis), () -> sendBatch(minecraftUuid, token, players, retry + 1)
                .whenComplete((result, error) -> {
                    if (error == null) delayed.complete(result);
                    else delayed.completeExceptionally(error);
                }));
        return delayed;
    }

    private static CompletableFuture<InstaBanLookupOutcome> combineBatches(List<CompletableFuture<BatchOutcome>> batches) {
        CompletableFuture<Void> all = CompletableFuture.allOf(batches.toArray(CompletableFuture[]::new));
        return all.thenApply(ignored -> {
            List<InstaBanResult> results = new ArrayList<>();
            String indexStatus = "fresh";
            Instant checkedAt = null;
            for (CompletableFuture<BatchOutcome> batchFuture : batches) {
                BatchOutcome batch = batchFuture.join();
                if (batch.failure() != null) return InstaBanLookupOutcome.failure(batch.failure());
                InstaBanCheckResponse response = batch.response();
                results.addAll(response.results());
                if (response.stale()) indexStatus = "stale";
                if (response.indexCheckedAt() != null
                        && (checkedAt == null || response.indexCheckedAt().isAfter(checkedAt))) {
                    checkedAt = response.indexCheckedAt();
                }
            }
            return InstaBanLookupOutcome.success(new InstaBanCheckResponse(indexStatus, checkedAt, results));
        });
    }

    static HttpRequest createRequest(String token, List<String> players) {
        if (token == null || token.isBlank() || players == null || players.isEmpty()
                || players.size() > MAX_PLAYERS_PER_REQUEST
                || players.stream().anyMatch(player -> !InputValidators.isUsername(player))) {
            throw new IllegalArgumentException("Invalid Do-Not-Insta-Ban request");
        }

        JsonArray playerArray = new JsonArray();
        players.forEach(playerArray::add);
        JsonObject body = new JsonObject();
        body.add("players", playerArray);
        String functionBody = body.toString();
        if (functionBody.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Do-Not-Insta-Ban body exceeds the API limit");
        }

        JsonObject executionBody = new JsonObject();
        executionBody.addProperty("body", functionBody);
        executionBody.addProperty("async", false);
        executionBody.addProperty("path", "/v1/do-not-insta-ban/check");
        executionBody.addProperty("method", "POST");
        JsonObject forwardedHeaders = new JsonObject();
        forwardedHeaders.addProperty("authorization", "Bearer " + token);
        forwardedHeaders.addProperty("content-type", "application/json");
        forwardedHeaders.addProperty("accept", "application/json");
        forwardedHeaders.addProperty("cache-control", "no-store");
        executionBody.add("headers", forwardedHeaders);

        return HttpRequest.newBuilder(EXECUTION_URI)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Appwrite-Project", APPWRITE_PROJECT)
                .header("User-Agent", "DuperKnight/DMLS")
                .POST(HttpRequest.BodyPublishers.ofString(executionBody.toString()))
                .build();
    }

    static TransportResponse unwrapExecutionResponse(TransportResponse outer) {
        if (outer.statusCode() < 200 || outer.statusCode() >= 300) return outer;
        JsonObject execution = JsonParser.parseString(outer.body()).getAsJsonObject();
        JsonElement statusElement = execution.get("responseStatusCode");
        JsonElement bodyElement = execution.get("responseBody");
        if (statusElement == null || !statusElement.isJsonPrimitive()
                || bodyElement == null || !bodyElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("Malformed Appwrite execution response");
        }
        return new TransportResponse(statusElement.getAsInt(), bodyElement.getAsString(),
                executionHeader(execution.get("responseHeaders"), "retry-after"));
    }

    private static String executionHeader(JsonElement headers, String requestedName) {
        if (headers == null || headers.isJsonNull()) return "";
        if (headers.isJsonArray()) {
            for (JsonElement element : headers.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject header = element.getAsJsonObject();
                JsonElement name = header.get("name");
                JsonElement value = header.get("value");
                if (name != null && value != null && requestedName.equalsIgnoreCase(name.getAsString())) {
                    return value.getAsString();
                }
            }
        } else if (headers.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : headers.getAsJsonObject().entrySet()) {
                if (requestedName.equalsIgnoreCase(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    return entry.getValue().getAsString();
                }
            }
        }
        return "";
    }

    static InstaBanCheckResponse parseSuccess(String body, List<String> expectedPlayers) {
        if (expectedPlayers == null) throw new IllegalArgumentException("Missing expected players");
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String indexStatus = requiredString(root, "indexStatus");
        if (!"fresh".equals(indexStatus) && !"stale".equals(indexStatus)) {
            throw new IllegalArgumentException("Unknown index status");
        }
        Instant checkedAt = optionalInstant(root, "indexCheckedAt");
        JsonElement resultsElement = root.get("results");
        if (resultsElement == null || !resultsElement.isJsonArray()
                || resultsElement.getAsJsonArray().size() != expectedPlayers.size()) {
            throw new IllegalArgumentException("Result count mismatch");
        }

        List<InstaBanResult> results = new ArrayList<>();
        for (int index = 0; index < expectedPlayers.size(); index++) {
            JsonElement element = resultsElement.getAsJsonArray().get(index);
            JsonObject result = element.getAsJsonObject();
            String ign = requiredString(result, "ign");
            if (!InputValidators.isUsername(ign) || !ign.equalsIgnoreCase(expectedPlayers.get(index))) {
                throw new IllegalArgumentException("Result player mismatch");
            }
            InstaBanStatus status = InstaBanStatus.parse(requiredString(result, "status"));
            InstaBanReason reason = InstaBanReason.parse(requiredString(result, "reason"));
            if (reason == InstaBanReason.UNKNOWN) status = InstaBanStatus.UNSURE;
            results.add(new InstaBanResult(ign, status, reason,
                    optionalDiscordUri(result, "messageUrl"), optionalInstant(result, "messageTimestamp")));
        }
        return new InstaBanCheckResponse(indexStatus, checkedAt, results);
    }

    static long retryAfterMillis(String value) {
        if (value == null) return 1_000L;
        try {
            return Math.clamp(Long.parseLong(value.trim()), 1L, 300L) * 1_000L;
        } catch (NumberFormatException ignored) {
            return 1_000L;
        }
    }

    private static long exponentialDelayMillis(int retry) {
        return (1L << Math.min(2, retry)) * 1_000L;
    }

    private static String errorCode(String body) {
        try {
            JsonElement value = JsonParser.parseString(body).getAsJsonObject().get("error");
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return value.getAsString();
    }

    private static Instant optionalInstant(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        try {
            return Instant.parse(value.getAsString());
        } catch (DateTimeParseException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static URI optionalDiscordUri(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        try {
            URI uri = URI.create(value.getAsString());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (host.equals("discord.com") || host.endsWith(".discord.com"))
                    && uri.getPath() != null && uri.getPath().startsWith("/channels/") ? uri : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static <T> CompletableFuture<T> dependent(CompletableFuture<T> source) {
        return source.thenApply(value -> value);
    }

    interface Transport {
        CompletableFuture<TransportResponse> send(HttpRequest request);
    }

    interface CredentialAccess {
        Optional<String> load(UUID uuid);

        void delete(UUID uuid);
    }

    interface RetryScheduler {
        void schedule(long delayMillis, Runnable task);
    }

    record TransportResponse(int statusCode, String body, String retryAfter) {
    }

    private record BatchOutcome(InstaBanCheckResponse response, InstaBanLookupOutcome.Type failure) {
        static BatchOutcome success(InstaBanCheckResponse response) { return new BatchOutcome(response, null); }
        static BatchOutcome failure(InstaBanLookupOutcome.Type type) { return new BatchOutcome(null, type); }
    }

    private record RequestKey(UUID minecraftUuid, List<String> players) {
        RequestKey {
            players = List.copyOf(players);
        }
    }

    private record AppwriteRequestKey(UUID minecraftUuid, String token, List<String> players) {
        AppwriteRequestKey {
            players = List.copyOf(players);
        }
    }
}
