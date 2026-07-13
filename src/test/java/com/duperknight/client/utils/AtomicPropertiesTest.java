package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicPropertiesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesACompleteReadableSnapshot() throws Exception {
        Path target = temporaryDirectory.resolve("nested/settings.properties");
        Properties properties = new Properties();
        properties.setProperty("one", "first");
        properties.setProperty("two", "second");

        AtomicProperties.store(target, properties, "test");

        Properties loaded = load(target);
        assertEquals("first", loaded.getProperty("one"));
        assertEquals("second", loaded.getProperty("two"));
    }

    @Test
    void replacementFailurePreservesPriorFileAndCleansTemporaryFile() throws Exception {
        Path target = temporaryDirectory.resolve("settings.properties");
        Properties prior = new Properties();
        prior.setProperty("mode", "safe");
        AtomicProperties.store(target, prior, "prior");
        byte[] priorBytes = Files.readAllBytes(target);

        Properties candidate = new Properties();
        candidate.setProperty("mode", "dangerous");
        assertThrows(IOException.class, () -> AtomicProperties.store(target, candidate, "candidate",
                (temporary, ignored) -> {
                    throw new IOException("simulated replacement failure");
                }));

        assertArrayEquals(priorBytes, Files.readAllBytes(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count(), "failed stores must not strand a sibling temporary file");
        }
    }

    @Test
    void nonAtomicFallbackRestoresPriorBytesEvenIfReplacementDamagesTarget() throws Exception {
        Path target = temporaryDirectory.resolve("settings.properties");
        Files.writeString(target, "mode=safe\n");
        byte[] priorBytes = Files.readAllBytes(target);
        Path candidate = temporaryDirectory.resolve("candidate.tmp");
        Files.writeString(candidate, "mode=dangerous\n");

        assertThrows(IOException.class, () -> AtomicProperties.replaceNonAtomically(
                candidate, target, (source, destination) -> {
                    Files.writeString(destination, "partial");
                    throw new IOException("simulated non-atomic failure");
                }));

        assertArrayEquals(priorBytes, Files.readAllBytes(target));
        assertEquals("mode=dangerous\n", Files.readString(candidate));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(2, files.count(), "fallback must clean its sibling backup after restoration");
        }
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
