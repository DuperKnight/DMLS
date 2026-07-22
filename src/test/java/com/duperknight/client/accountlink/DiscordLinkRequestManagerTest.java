package com.duperknight.client.accountlink;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiscordLinkRequestManagerTest {
    @Test
    void coalescesIdenticalRequestsAndDeliversTheResultToEveryCaller() {
        DiscordLinkRequestManager manager = new DiscordLinkRequestManager();
        CompletableFuture<String> networkResult = new CompletableFuture<>();
        AtomicInteger starts = new AtomicInteger();

        CompletableFuture<String> first = manager.submit("same-status", () -> {
            starts.incrementAndGet();
            return networkResult;
        });
        CompletableFuture<String> second = manager.submit("same-status", () -> {
            starts.incrementAndGet();
            return CompletableFuture.completedFuture("wrong");
        });

        assertEquals(1, starts.get());
        networkResult.complete("linked");
        assertEquals("linked", first.join());
        assertEquals("linked", second.join());
    }

    @Test
    void queuesDifferentRequestsUntilTheCurrentRequestFinishes() {
        DiscordLinkRequestManager manager = new DiscordLinkRequestManager();
        CompletableFuture<String> firstNetworkResult = new CompletableFuture<>();
        AtomicInteger secondStarts = new AtomicInteger();

        CompletableFuture<String> first = manager.submit("status", () -> firstNetworkResult);
        CompletableFuture<String> second = manager.submit("unlink", () -> {
            secondStarts.incrementAndGet();
            return CompletableFuture.completedFuture("unlinked");
        });

        assertEquals(0, secondStarts.get());
        assertFalse(second.isDone());
        firstNetworkResult.complete("linked");
        assertEquals("linked", first.join());
        assertEquals("unlinked", second.join());
        assertEquals(1, secondStarts.get());
    }
}
