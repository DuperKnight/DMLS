package com.duperknight.client.utils;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AlertWordlist {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls-alerts.txt");
    private static final String TEMPLATE = """
            # DMLS alert words, one per line. Lines starting with # are ignored.
            # Matching ignores case, spacing, accents, repeated letters and leetspeak,
            # so entries also catch bypasses like "w o r d", "w0rd" or "wooord".
            # Run /dmls alerts reload after editing.
            """;

    private volatile List<Entry> entries = List.of();

    private record Entry(String raw, String normalized, String collapsed) {
    }

    /**
     * Loads the wordlist from disk, creating a template file if it doesn't exist yet.
     *
     * @return the number of loaded words
     */
    public int load() {
        if (!Files.exists(PATH)) {
            try {
                Files.writeString(PATH, TEMPLATE);
            } catch (IOException e) {
                DMLS.LOGGER.warn("Failed to create {}", PATH, e);
            }
        }

        List<Entry> loaded = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(PATH)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String normalized = ChatNormalizer.normalize(trimmed);
                if (!normalized.isEmpty()) {
                    loaded.add(new Entry(trimmed, normalized, ChatNormalizer.collapseRepeats(normalized)));
                }
            }
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}", PATH, e);
        }

        entries = List.copyOf(loaded);
        return entries.size();
    }

    public int size() {
        return entries.size();
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
