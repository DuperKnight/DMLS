package com.duperknight.client.rulebook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RulebookCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsOneValidatedCacheEnvelope() throws Exception {
        RulebookCache cache = new RulebookCache(temporaryDirectory.resolve("cache.zip"));
        Instant fetchedAt = Instant.parse("2026-07-20T19:30:00Z");
        cache.store(RulebookFixtures.document(), fetchedAt);

        RulebookCache.CacheEntry loaded = cache.load();
        assertEquals(RulebookFixtures.document(), loaded.html());
        assertEquals(fetchedAt, loaded.fetchedAt());
        assertEquals(RulebookCache.sha256(loaded.html().getBytes(java.nio.charset.StandardCharsets.UTF_8)), loaded.sha256());
    }

    @Test
    void rejectsCorruptAndOversizedInputs() throws Exception {
        Path path = temporaryDirectory.resolve("cache.zip");
        Files.write(path, new byte[]{1, 2, 3, 4});
        assertThrows(IOException.class, () -> new RulebookCache(path).load());
        assertThrows(IOException.class, () -> RulebookCache.readBounded(new ByteArrayInputStream(new byte[11]), 10));
    }
}
