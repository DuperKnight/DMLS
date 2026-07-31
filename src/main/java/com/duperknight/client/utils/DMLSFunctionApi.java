package com.duperknight.client.utils;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Shared public HTTP domain for routes served by the DMLS Appwrite Function. */
public final class DMLSFunctionApi {
    private static final URI BASE_URI = URI.create("https://dmls-linking.appwrite.network");

    private DMLSFunctionApi() {
    }

    public static URI route(String path) {
        if (path == null || path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Function route must start with '/'");
        }
        return BASE_URI.resolve(path);
    }

    public static HttpRequest.Builder request(String path, Duration timeout) {
        return HttpRequest.newBuilder(route(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "DuperKnight/DMLS");
    }
}
