package com.duperknight.client.accountlink;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coalesces identical Appwrite operations and runs different operations one at a time. */
public final class DiscordLinkRequestManager {
    private static final DiscordLinkRequestManager SHARED = new DiscordLinkRequestManager();
    private final Object lock = new Object();
    private final ArrayDeque<QueuedRequest<?>> queue = new ArrayDeque<>();
    private final Map<Object, CompletableFuture<?>> sharedRequests = new HashMap<>();
    private boolean requestRunning;

    public static DiscordLinkRequestManager shared() {
        return SHARED;
    }

    public <T> CompletableFuture<T> submit(Object key, Supplier<CompletableFuture<T>> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");

        QueuedRequest<?> requestToStart = null;
        CompletableFuture<T> sharedResult;
        synchronized (lock) {
            @SuppressWarnings("unchecked")
            CompletableFuture<T> existing = (CompletableFuture<T>) sharedRequests.get(key);
            if (existing != null) return dependentView(existing);

            sharedResult = new CompletableFuture<>();
            sharedRequests.put(key, sharedResult);
            queue.addLast(new QueuedRequest<>(key, operation, sharedResult));
            if (!requestRunning) {
                requestRunning = true;
                requestToStart = queue.removeFirst();
            }
        }

        if (requestToStart != null) start(requestToStart);
        return dependentView(sharedResult);
    }

    private <T> void start(QueuedRequest<T> request) {
        CompletableFuture<T> operationFuture;
        try {
            operationFuture = Objects.requireNonNull(request.operation().get(), "operation future");
        } catch (Throwable error) {
            operationFuture = CompletableFuture.failedFuture(error);
        }
        operationFuture.whenComplete((result, error) -> {
            if (error == null) request.result().complete(result);
            else request.result().completeExceptionally(error);
            finish(request);
        });
    }

    private void finish(QueuedRequest<?> finishedRequest) {
        QueuedRequest<?> requestToStart;
        synchronized (lock) {
            sharedRequests.remove(finishedRequest.key(), finishedRequest.result());
            requestToStart = queue.pollFirst();
            if (requestToStart == null) requestRunning = false;
        }
        if (requestToStart != null) start(requestToStart);
    }

    private static <T> CompletableFuture<T> dependentView(CompletableFuture<T> sharedResult) {
        return sharedResult.thenApply(Function.identity());
    }

    private record QueuedRequest<T>(Object key, Supplier<CompletableFuture<T>> operation,
                                    CompletableFuture<T> result) {
    }
}
