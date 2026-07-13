package com.duperknight.client.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/** Writes a complete properties snapshot without truncating the previous file first. */
public final class AtomicProperties {
    private AtomicProperties() {
    }

    /**
     * Stores {@code properties} in a sibling temporary file and then replaces {@code target}.
     * If the filesystem does not support atomic moves, a normal replacing move is used instead.
     * A failure before replacement leaves the prior target untouched.
     */
    public static void store(Path target, Properties properties, String comment) throws IOException {
        store(target, properties, comment, AtomicProperties::replace);
    }

    static void store(Path target, Properties properties, String comment, Replacer replacer) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(replacer, "replacer");

        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Properties target has no parent: " + target);
        }
        Files.createDirectories(parent);

        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        String fileName = absoluteTarget.getFileName().toString();
        String prefix = "." + (fileName.length() < 2 ? fileName + "__" : fileName) + ".";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean replaced = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                snapshot.store(output, comment);
            }
            replacer.replace(temporary, absoluteTarget);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            replaceNonAtomically(temporary, target,
                    (source, destination) -> Files.move(
                            source, destination, StandardCopyOption.REPLACE_EXISTING));
        }
    }

    /**
     * Fallback for filesystems without atomic replacement. A sibling backup is retained until
     * replacement succeeds, and a failed replacement restores the exact prior target bytes.
     */
    static void replaceNonAtomically(Path temporary, Path target, Replacer mover) throws IOException {
        Objects.requireNonNull(temporary, "temporary");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mover, "mover");

        boolean targetExisted = Files.exists(target);
        Path backup = null;
        if (targetExisted) {
            String fileName = target.getFileName().toString();
            String prefix = "." + (fileName.length() < 2 ? fileName + "__" : fileName) + ".";
            backup = Files.createTempFile(target.toAbsolutePath().getParent(), prefix, ".bak");
            try {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException exception) {
                Files.deleteIfExists(backup);
                throw exception;
            }
        }

        try {
            mover.replace(temporary, target);
        } catch (IOException replacementFailure) {
            try {
                if (targetExisted) {
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException restoreFailure) {
                replacementFailure.addSuppressed(restoreFailure);
                // Keep the backup beside the target so the previous bytes remain recoverable.
                backup = null;
            }
            throw replacementFailure;
        } finally {
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    // The target is already committed or restored; cleanup must not make callers
                    // roll back their in-memory state after a successful disk replacement.
                }
            }
        }
    }

    @FunctionalInterface
    interface Replacer {
        void replace(Path temporary, Path target) throws IOException;
    }
}
