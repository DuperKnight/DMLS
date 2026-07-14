package com.duperknight.client.utils;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AlertWordlist {
    private static final String TEMPLATE = """
            # DMLS alert words, one per line. Lines starting with # are ignored.
            # Matching ignores case, spacing, accents, repeated letters and leetspeak,
            # so entries also catch bypasses like "w o r d", "w0rd" or "wooord".
            # Run /dmls alerts reload after editing.
            """;

    private final Path path;
    private volatile List<Entry> entries = List.of();
    private volatile LoadResult lastLoadResult;

    public enum LoadStatus {
        NOT_LOADED,
        LOADED,
        FAILED
    }

    /** Outcome of a wordlist reload. A failure leaves the previous entries active. */
    public record LoadResult(LoadStatus status, int wordCount, Path path) {
        public boolean successful() {
            return status == LoadStatus.LOADED;
        }
    }

    private record Entry(String raw, String normalized, String collapsed) {
    }

    public AlertWordlist() {
        this(FabricLoader.getInstance().getConfigDir().resolve("dmls-alerts.txt"));
    }

    /** Allows tests and alternate installations to use an explicit wordlist path. */
    public AlertWordlist(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.lastLoadResult = new LoadResult(LoadStatus.NOT_LOADED, 0, path);
    }

    /**
     * Loads the wordlist from disk, creating a template file if it doesn't exist yet.
     *
     * @return a typed result containing the active word count
     */
    public LoadResult load() {
        try {
            createTemplateIfMissing();

            // Keep insertion order so the first spelling in the file is the one
            // shown to staff, while normalized duplicates are loaded only once.
            Map<String, Entry> loaded = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String normalized = ChatNormalizer.normalize(trimmed);
                if (!normalized.isEmpty()) {
                    loaded.putIfAbsent(normalized,
                            new Entry(trimmed, normalized, ChatNormalizer.collapseRepeats(normalized)));
                }
            }

            entries = List.copyOf(loaded.values());
            lastLoadResult = new LoadResult(LoadStatus.LOADED, entries.size(), path);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to load {}; keeping the previous alert wordlist", path, e);
            lastLoadResult = new LoadResult(LoadStatus.FAILED, entries.size(), path);
        }
        return lastLoadResult;
    }

    private void createTemplateIfMissing() throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.writeString(path, TEMPLATE, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            // Another reload created it between the existence check and write.
        }
    }

    public int size() {
        return entries.size();
    }

    public LoadResult lastLoadResult() {
        return lastLoadResult;
    }

    public Optional<String> findMatch(String text) {
        List<Entry> currentEntries = entries;
        if (currentEntries.isEmpty()) {
            return Optional.empty();
        }

        String normalized = ChatNormalizer.normalize(text);
        String collapsed = ChatNormalizer.collapseRepeats(normalized);
        for (Entry entry : currentEntries) {
            if (normalized.contains(entry.normalized()) || collapsed.contains(entry.collapsed())) {
                return Optional.of(entry.raw());
            }
        }
        return Optional.empty();
    }
}
