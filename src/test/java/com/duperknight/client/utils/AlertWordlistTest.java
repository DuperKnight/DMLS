package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertWordlistTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsAnEmptyTemplateAtAnInjectedPath() {
        Path path = temporaryDirectory.resolve("nested/alerts.txt");
        AlertWordlist wordlist = new AlertWordlist(path);

        AlertWordlist.LoadResult result = wordlist.load();

        assertTrue(result.successful());
        assertEquals(0, result.wordCount());
        assertTrue(Files.isRegularFile(path));
    }

    @Test
    void deduplicatesEntriesByNormalizedValueAndPreservesFirstSpelling() throws IOException {
        Path path = temporaryDirectory.resolve("alerts.txt");
        Files.writeString(path, "Bad\nb a d\nBÁD\n# ignored\n\nother\n");
        AlertWordlist wordlist = new AlertWordlist(path);

        AlertWordlist.LoadResult result = wordlist.load();

        assertEquals(2, result.wordCount());
        assertEquals("Bad", wordlist.findMatch("That was b 4 a d").orElseThrow());
        assertEquals("other", wordlist.findMatch("OTHER").orElseThrow());
    }

    @Test
    void failedReloadKeepsTheLastKnownGoodEntries() throws IOException {
        Path path = temporaryDirectory.resolve("alerts.txt");
        Files.writeString(path, "danger\n");
        AlertWordlist wordlist = new AlertWordlist(path);
        assertTrue(wordlist.load().successful());

        Files.delete(path);
        Files.createDirectory(path);
        AlertWordlist.LoadResult failed = wordlist.load();

        assertFalse(failed.successful());
        assertEquals(AlertWordlist.LoadStatus.FAILED, failed.status());
        assertEquals(1, failed.wordCount());
        assertEquals("danger", wordlist.findMatch("DANGER").orElseThrow());
    }
}
