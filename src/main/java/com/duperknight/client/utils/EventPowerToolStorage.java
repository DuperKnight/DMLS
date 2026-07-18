package com.duperknight.client.utils;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Persisted named /powertool command bindings, saved client-side. */
public final class EventPowerToolStorage {
    private static final Map<String, String> ENTRIES = new LinkedHashMap<>();
    private static boolean loaded;

    private EventPowerToolStorage() {
    }

    public static synchronized Map<String, String> all() {
        ensureLoaded();
        return Map.copyOf(ENTRIES);
    }

    public static synchronized Optional<String> get(String name) {
        ensureLoaded();
        return Optional.ofNullable(ENTRIES.get(name));
    }

    public static synchronized boolean save(String name, String command) {
        ensureLoaded();
        String previous = ENTRIES.put(name, command);
        if (persist()) return true;
        if (previous == null) ENTRIES.remove(name); else ENTRIES.put(name, previous);
        return false;
    }

    public static synchronized boolean delete(String name) {
        ensureLoaded();
        if (!ENTRIES.containsKey(name)) return false;
        String previous = ENTRIES.remove(name);
        if (persist()) return true;
        ENTRIES.put(name, previous);
        return false;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = storagePath();
        if (!Files.exists(path)) return;

        Properties properties = new Properties();
        try (var in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}, using empty powertool storage", path, e);
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            ENTRIES.put(key, properties.getProperty(key));
        }
    }

    private static boolean persist() {
        Properties properties = new Properties();
        ENTRIES.forEach(properties::setProperty);
        try {
            AtomicProperties.store(storagePath(), properties, "DMLS powertool bindings");
            return true;
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to save {}", storagePath(), e);
            return false;
        }
    }

    private static Path storagePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls-powertools.properties");
    }
}