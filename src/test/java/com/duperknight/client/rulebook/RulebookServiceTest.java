package com.duperknight.client.rulebook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RulebookServiceTest {
    @TempDir
    Path temporaryDirectory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Instant now = Instant.parse("2026-07-20T19:45:00Z");

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void successfulRefreshBecomesLiveAndPersistsCache() throws Exception {
        RulebookCache cache = new RulebookCache(temporaryDirectory.resolve("cache.zip"));
        RulebookService service = service(() -> RulebookFixtures.document(), cache);

        RulebookSnapshot snapshot = service.refresh().get(5, TimeUnit.SECONDS);
        assertEquals(RulebookStatus.LIVE, snapshot.status());
        assertEquals(now, snapshot.fetchedAt());
        assertFalse(snapshot.refreshing());
        assertNotNull(cache.load());
    }

    @Test
    void cachedCopyStaysStaleWhenRefreshFails() throws Exception {
        RulebookCache cache = new RulebookCache(temporaryDirectory.resolve("cache.zip"));
        cache.store(RulebookFixtures.document(), now.minusSeconds(3600));
        RulebookService service = service(() -> { throw new IOException("offline"); }, cache);

        service.loadCache();
        assertEquals(RulebookStatus.STALE, service.snapshot().status());
        RulebookSnapshot failed = service.refresh().get(5, TimeUnit.SECONDS);
        assertEquals(RulebookStatus.STALE, failed.status());
        assertTrue(failed.hasDocument());
        assertEquals("offline", failed.error());
    }

    @Test
    void firstRunFailureIsUnavailableAndConcurrentReloadsCoalesce() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RulebookService service = service(() -> {
            release.await(5, TimeUnit.SECONDS);
            throw new IOException("offline");
        }, new RulebookCache(temporaryDirectory.resolve("cache.zip")));

        CompletableFuture<RulebookSnapshot> first = service.refresh();
        CompletableFuture<RulebookSnapshot> second = service.refresh();
        assertSame(first, second);
        release.countDown();
        assertEquals(RulebookStatus.UNAVAILABLE, first.get(5, TimeUnit.SECONDS).status());
    }

    private RulebookService service(RulebookService.Source source, RulebookCache cache) {
        return new RulebookService(source, cache, Clock.fixed(now, ZoneOffset.UTC), executor);
    }
}
