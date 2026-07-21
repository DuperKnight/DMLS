package com.duperknight.client.accountlink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/** Creates Discord account-link requests through the public Appwrite Function execution API. */
public final class DiscordLinkService {
    private static final URI EXECUTION_URI = URI.create(
            "https://fra.cloud.appwrite.io/v1/functions/dmls-linking/executions");
    private static final String APPWRITE_PROJECT = "68305f510028a84a7227";
    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern LINK_CODE = Pattern.compile("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
    private static final Pattern CLIENT_TOKEN = Pattern.compile("dmls_[A-Za-z0-9_-]{43}");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private DiscordLinkService() {
    }

    public static CompletableFuture<Result> request(UUID minecraftUuid, String minecraftUsername) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");

        JsonObject functionBody = new JsonObject();
        functionBody.addProperty("minecraftUuid", minecraftUuid.toString().toLowerCase());
        if (minecraftUsername != null && USERNAME.matcher(minecraftUsername).matches()) {
            functionBody.addProperty("minecraftUsername", minecraftUsername);
        }

        JsonObject executionBody = new JsonObject();
        executionBody.addProperty("body", functionBody.toString());
        executionBody.addProperty("async", false);
        executionBody.addProperty("path", "/v1/link-requests");
        executionBody.addProperty("method", "POST");
        JsonObject forwardedHeaders = new JsonObject();
        forwardedHeaders.addProperty("content-type", "application/json");
        executionBody.add("headers", forwardedHeaders);

        HttpRequest request = HttpRequest.newBuilder(EXECUTION_URI)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Appwrite-Project", APPWRITE_PROJECT)
                .header("User-Agent", "DuperKnight/DMLS")
                .POST(HttpRequest.BodyPublishers.ofString(executionBody.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) return failureResult(error);
                    return parseExecutionResponse(response.statusCode(), response.body());
                });
    }

    public static CompletableFuture<LinkStatusResult> checkStatus(UUID minecraftUuid, String clientToken) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        if (clientToken == null || !CLIENT_TOKEN.matcher(clientToken).matches()) {
            return CompletableFuture.completedFuture(LinkStatusResult.failure(LinkStatus.INVALID_TOKEN, ""));
        }

        JsonObject executionBody = new JsonObject();
        executionBody.addProperty("body", "");
        executionBody.addProperty("async", false);
        executionBody.addProperty("path", "/v1/link-status");
        executionBody.addProperty("method", "GET");
        JsonObject forwardedHeaders = new JsonObject();
        forwardedHeaders.addProperty("authorization", "Bearer " + clientToken);
        executionBody.add("headers", forwardedHeaders);

        HttpRequest request = HttpRequest.newBuilder(EXECUTION_URI)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Appwrite-Project", APPWRITE_PROJECT)
                .header("User-Agent", "DuperKnight/DMLS")
                .POST(HttpRequest.BodyPublishers.ofString(executionBody.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) return statusFailureResult(error);
                    return parseStatusExecutionResponse(response.statusCode(), response.body(), minecraftUuid);
                });
    }

    static Result parseExecutionResponse(int httpStatus, String body) {
        try {
            JsonObject outer = JsonParser.parseString(body).getAsJsonObject();
            if (httpStatus == 429) return Result.failure(Status.RATE_LIMITED, outerMessage(outer));
            if (httpStatus < 200 || httpStatus >= 300) {
                return Result.failure(Status.SERVICE_ERROR, outerMessage(outer));
            }

            JsonElement responseStatusElement = outer.get("responseStatusCode");
            JsonElement responseBodyElement = outer.get("responseBody");
            if (responseStatusElement == null || !responseStatusElement.isJsonPrimitive()
                    || responseBodyElement == null || !responseBodyElement.isJsonPrimitive()) {
                return Result.failure(Status.MALFORMED_RESPONSE, "");
            }

            int responseStatus = responseStatusElement.getAsInt();
            JsonObject functionResponse = JsonParser.parseString(responseBodyElement.getAsString()).getAsJsonObject();
            if (responseStatus == 429) {
                return Result.failure(Status.RATE_LIMITED, functionMessage(functionResponse));
            }
            if (responseStatus != 201) {
                return Result.failure(Status.SERVICE_ERROR, functionMessage(functionResponse));
            }

            String code = requiredString(functionResponse, "code");
            String clientToken = requiredString(functionResponse, "clientToken");
            String expiresAt = requiredString(functionResponse, "expiresAt");
            int pollAfterSeconds = functionResponse.get("pollAfterSeconds").getAsInt();
            if (!LINK_CODE.matcher(code).matches() || !CLIENT_TOKEN.matcher(clientToken).matches()
                    || expiresAt.isBlank() || pollAfterSeconds < 1) {
                return Result.failure(Status.MALFORMED_RESPONSE, "");
            }
            return Result.success(code, clientToken, expiresAt, pollAfterSeconds);
        } catch (RuntimeException error) {
            return Result.failure(Status.MALFORMED_RESPONSE, "");
        }
    }

    static LinkStatusResult parseStatusExecutionResponse(int httpStatus, String body, UUID expectedUuid) {
        try {
            JsonObject outer = JsonParser.parseString(body).getAsJsonObject();
            if (httpStatus == 429) {
                return LinkStatusResult.failure(LinkStatus.RATE_LIMITED, outerMessage(outer));
            }
            if (httpStatus < 200 || httpStatus >= 300) {
                return LinkStatusResult.failure(LinkStatus.SERVICE_ERROR, outerMessage(outer));
            }

            JsonElement statusElement = outer.get("responseStatusCode");
            JsonElement bodyElement = outer.get("responseBody");
            if (statusElement == null || !statusElement.isJsonPrimitive()
                    || bodyElement == null || !bodyElement.isJsonPrimitive()) {
                return LinkStatusResult.failure(LinkStatus.MALFORMED_RESPONSE, "");
            }

            int responseStatus = statusElement.getAsInt();
            JsonObject functionResponse = JsonParser.parseString(bodyElement.getAsString()).getAsJsonObject();
            if (responseStatus == 401) {
                return LinkStatusResult.failure(LinkStatus.INVALID_TOKEN, functionMessage(functionResponse));
            }
            if (responseStatus == 403) {
                return LinkStatusResult.failure(LinkStatus.AUTHORIZATION_STALE, functionMessage(functionResponse));
            }
            if (responseStatus == 410) {
                return LinkStatusResult.failure(LinkStatus.EXPIRED, functionMessage(functionResponse));
            }
            if (responseStatus == 429) {
                return LinkStatusResult.failure(LinkStatus.RATE_LIMITED, functionMessage(functionResponse));
            }
            if (responseStatus != 200) {
                return LinkStatusResult.failure(LinkStatus.SERVICE_ERROR, functionMessage(functionResponse));
            }

            String status = requiredString(functionResponse, "status");
            String minecraftUuid = requiredString(functionResponse, "minecraftUuid");
            if (!expectedUuid.toString().equalsIgnoreCase(minecraftUuid)) {
                return LinkStatusResult.failure(LinkStatus.MALFORMED_RESPONSE, "");
            }
            if ("linked".equals(status)) return LinkStatusResult.linked();
            if (!"pending".equals(status)) {
                return LinkStatusResult.failure(LinkStatus.MALFORMED_RESPONSE, "");
            }

            JsonElement pollElement = functionResponse.get("pollAfterSeconds");
            int pollAfterSeconds = pollElement == null ? 5 : pollElement.getAsInt();
            if (pollAfterSeconds < 1) {
                return LinkStatusResult.failure(LinkStatus.MALFORMED_RESPONSE, "");
            }
            return LinkStatusResult.pending(pollAfterSeconds);
        } catch (RuntimeException error) {
            return LinkStatusResult.failure(LinkStatus.MALFORMED_RESPONSE, "");
        }
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalStateException("Missing response field: " + name);
        }
        return primitive.getAsString();
    }

    private static String outerMessage(JsonObject object) {
        JsonElement message = object.get("message");
        return message != null && message.isJsonPrimitive() ? message.getAsString() : "";
    }

    private static String functionMessage(JsonObject object) {
        JsonElement message = object.get("message");
        return message != null && message.isJsonPrimitive() ? message.getAsString() : "";
    }

    private static Result failureResult(Throwable error) {
        Throwable cause = unwrap(error);
        return Result.failure(cause instanceof java.net.http.HttpTimeoutException
                ? Status.TIMEOUT : Status.NETWORK_ERROR, "");
    }

    private static LinkStatusResult statusFailureResult(Throwable error) {
        Throwable cause = unwrap(error);
        return LinkStatusResult.failure(cause instanceof java.net.http.HttpTimeoutException
                ? LinkStatus.TIMEOUT : LinkStatus.NETWORK_ERROR, "");
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public record Result(Status status, String code, String clientToken, String expiresAt,
                         int pollAfterSeconds, String message) {
        public Result {
            Objects.requireNonNull(status, "status");
            code = code == null ? "" : code;
            clientToken = clientToken == null ? "" : clientToken;
            expiresAt = expiresAt == null ? "" : expiresAt;
            message = message == null ? "" : message;
        }

        static Result success(String code, String clientToken, String expiresAt, int pollAfterSeconds) {
            return new Result(Status.SUCCESS, code, clientToken, expiresAt, pollAfterSeconds, "");
        }

        static Result failure(Status status, String message) {
            return new Result(status, "", "", "", 0, message);
        }

        public boolean succeeded() {
            return status == Status.SUCCESS;
        }
    }

    public enum Status {
        SUCCESS,
        RATE_LIMITED,
        TIMEOUT,
        NETWORK_ERROR,
        SERVICE_ERROR,
        MALFORMED_RESPONSE
    }

    public record LinkStatusResult(LinkStatus status, int pollAfterSeconds, String message) {
        public LinkStatusResult {
            Objects.requireNonNull(status, "status");
            message = message == null ? "" : message;
        }

        static LinkStatusResult linked() {
            return new LinkStatusResult(LinkStatus.LINKED, 0, "");
        }

        static LinkStatusResult pending(int pollAfterSeconds) {
            return new LinkStatusResult(LinkStatus.PENDING, pollAfterSeconds, "");
        }

        static LinkStatusResult failure(LinkStatus status, String message) {
            return new LinkStatusResult(status, 0, message);
        }
    }

    public enum LinkStatus {
        LINKED,
        PENDING,
        INVALID_TOKEN,
        AUTHORIZATION_STALE,
        EXPIRED,
        RATE_LIMITED,
        TIMEOUT,
        NETWORK_ERROR,
        SERVICE_ERROR,
        MALFORMED_RESPONSE
    }
}
