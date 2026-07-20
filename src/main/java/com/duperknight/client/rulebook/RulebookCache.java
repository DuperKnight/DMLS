package com.duperknight.client.rulebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** A single atomically replaceable cache envelope containing the HTML and its validation metadata. */
final class RulebookCache {
    static final int MAX_HTML_BYTES = 32 * 1024 * 1024;
    private static final String DOCUMENT_ENTRY = "document.html";
    private static final String METADATA_ENTRY = "metadata.properties";
    private final Path path;

    RulebookCache(Path path) {
        this.path = path;
    }

    CacheEntry load() throws IOException {
        if (!Files.exists(path)) return null;
        byte[] document = null;
        Properties metadata = null;
        try (InputStream input = Files.newInputStream(path); ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(DOCUMENT_ENTRY)) document = readBounded(zip, MAX_HTML_BYTES);
                else if (entry.getName().equals(METADATA_ENTRY)) {
                    byte[] encoded = readBounded(zip, 16 * 1024);
                    metadata = new Properties();
                    metadata.load(new java.io.ByteArrayInputStream(encoded));
                }
            }
        }
        if (document == null || metadata == null) throw new IOException("Rulebook cache is incomplete");
        String expectedHash = metadata.getProperty("sha256", "");
        String actualHash = sha256(document);
        if (!expectedHash.equalsIgnoreCase(actualHash)) throw new IOException("Rulebook cache checksum does not match");
        Instant fetchedAt;
        try {
            fetchedAt = Instant.parse(metadata.getProperty("fetchedAt", ""));
        } catch (RuntimeException exception) {
            throw new IOException("Rulebook cache timestamp is invalid", exception);
        }
        return new CacheEntry(new String(document, StandardCharsets.UTF_8), fetchedAt, actualHash);
    }

    void store(String html, Instant fetchedAt) throws IOException {
        byte[] document = html.getBytes(StandardCharsets.UTF_8);
        if (document.length > MAX_HTML_BYTES) throw new IOException("Rulebook is larger than 32 MiB");
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".dmls-rulebook-cache.", ".tmp");
        boolean moved = false;
        try {
            Properties metadata = new Properties();
            metadata.setProperty("fetchedAt", fetchedAt.toString());
            metadata.setProperty("sha256", sha256(document));
            try (OutputStream output = Files.newOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(DOCUMENT_ENTRY));
                zip.write(document);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(METADATA_ENTRY));
                ByteArrayOutputStream encodedMetadata = new ByteArrayOutputStream();
                metadata.store(encodedMetadata, "DMLS live rulebook cache");
                zip.write(encodedMetadata.toByteArray());
                zip.closeEntry();
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.min(maximum, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maximum) throw new IOException("Rulebook response exceeds the size limit");
            result.write(buffer, 0, read);
        }
        return result.toByteArray();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record CacheEntry(String html, Instant fetchedAt, String sha256) {
    }
}
