package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.modules.StaffRank;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Handles durable DMLS preferences, stored in config/dmls.properties.
 */
public final class DMLSConfig {
    public static final List<String> RANK_SUGGESTIONS = List.of("helper", "moderator", "senior_moderator", "support", "admin");

    private static final String RANK_KEY = "staffRank";
    private static final String ALERTS_KEY = "chatAlerts";
    private static final String TRADE_CHAT_MUTED_KEY = "tradeChatMuted";
    private static final String SERVER_MESSAGES_MUTED_KEY = "serverMessagesMuted";
    private static final String GREETER_ENABLED_KEY = "greeterEnabled";
    private static final String ALLOWED_SERVERS_KEY = "allowedServers";
    private static final StaffRank DEFAULT_RANK = StaffRank.HELPER;

    private static boolean loaded;
    private static StaffRank staffRank = DEFAULT_RANK;
    private static boolean alertsEnabled = true;
    private static boolean tradeChatMuted;
    private static boolean serverMessagesMuted;
    private static boolean greeterEnabled = true;
    private static List<String> allowedServers = ServerGuard.DEFAULT_ALLOWED_SERVERS;
    // Deliberately not persisted: the game always starts live, so a forgotten
    // dry run can never suppress real commands in a later session.
    private static boolean dryRun;

    private DMLSConfig() {
    }

    public static StaffRank staffRank() {
        ensureLoaded();
        return staffRank;
    }

    public static boolean setStaffRank(StaffRank rank) {
        ensureLoaded();
        StaffRank previous = staffRank;
        staffRank = rank;
        if (save()) return true;
        staffRank = previous;
        return false;
    }

    public static boolean alertsEnabled() {
        ensureLoaded();
        return alertsEnabled;
    }

    public static boolean setAlertsEnabled(boolean enabled) {
        ensureLoaded();
        boolean previous = alertsEnabled;
        alertsEnabled = enabled;
        if (save()) return true;
        alertsEnabled = previous;
        return false;
    }

    public static boolean tradeChatMuted() {
        ensureLoaded();
        return tradeChatMuted;
    }

    public static boolean setTradeChatMuted(boolean muted) {
        ensureLoaded();
        boolean previous = tradeChatMuted;
        tradeChatMuted = muted;
        if (save()) return true;
        tradeChatMuted = previous;
        return false;
    }

    public static boolean serverMessagesMuted() {
        ensureLoaded();
        return serverMessagesMuted;
    }

    public static boolean setServerMessagesMuted(boolean muted) {
        ensureLoaded();
        boolean previous = serverMessagesMuted;
        serverMessagesMuted = muted;
        if (save()) return true;
        serverMessagesMuted = previous;
        return false;
    }

    public static boolean greeterEnabled() {
        ensureLoaded();
        return greeterEnabled;
    }

    public static boolean setGreeterEnabled(boolean enabled) {
        ensureLoaded();
        boolean previous = greeterEnabled;
        greeterEnabled = enabled;
        if (save()) return true;
        greeterEnabled = previous;
        return false;
    }

    /** While enabled, DMLS prints every command instead of running it. Not persisted. */
    public static boolean dryRun() {
        return dryRun;
    }

    public static void setDryRun(boolean enabled) {
        dryRun = enabled;
    }

    public static List<String> allowedServers() {
        ensureLoaded();
        return allowedServers;
    }

    public static boolean setAllowedServers(List<String> servers) {
        ensureLoaded();
        List<String> previous = allowedServers;
        allowedServers = servers.stream()
                .map(ServerGuard::normalizeRule)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (save()) return true;
        allowedServers = previous;
        return false;
    }

    /**
     * Parses a staff rank from user input, also accepting aliases like "mod" and "srmod".
     *
     * @param input the input to parse
     * @return the parsed rank, or empty if the input is not a valid rank
     */
    public static Optional<StaffRank> parseRank(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (StaffRank rank : StaffRank.values()) {
            if (rank.name().equals(normalized)) {
                return Optional.of(rank);
            }
        }

        return switch (normalized) {
            case "MOD" -> Optional.of(StaffRank.MODERATOR);
            case "SRMOD", "SR_MOD", "SENIORMOD", "SENIOR_MOD" -> Optional.of(StaffRank.SENIOR_MODERATOR);
            default -> Optional.empty();
        };
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path configPath = configPath();
        if (!Files.exists(configPath)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}, using defaults", configPath, e);
            return;
        }

        staffRank = parseRank(properties.getProperty(RANK_KEY, "")).orElse(DEFAULT_RANK);
        alertsEnabled = Boolean.parseBoolean(properties.getProperty(ALERTS_KEY, "true"));
        tradeChatMuted = Boolean.parseBoolean(properties.getProperty(TRADE_CHAT_MUTED_KEY, "false"));
        serverMessagesMuted = Boolean.parseBoolean(properties.getProperty(SERVER_MESSAGES_MUTED_KEY, "false"));
        greeterEnabled = Boolean.parseBoolean(properties.getProperty(GREETER_ENABLED_KEY, "true"));
        if (properties.containsKey(ALLOWED_SERVERS_KEY)) {
            allowedServers = java.util.Arrays.stream(properties.getProperty(ALLOWED_SERVERS_KEY, "").split(","))
                    .map(ServerGuard::normalizeRule)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
        } else {
            allowedServers = ServerGuard.DEFAULT_ALLOWED_SERVERS;
        }
    }

    private static boolean save() {
        Properties properties = propertiesSnapshot();
        Path configPath = configPath();
        try {
            AtomicProperties.store(configPath, properties, "DMLS settings");
            return true;
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to save {}", configPath, e);
            return false;
        }
    }

    static Properties propertiesSnapshot() {
        Properties properties = new Properties();
        properties.setProperty(RANK_KEY, staffRank.name());
        properties.setProperty(ALERTS_KEY, Boolean.toString(alertsEnabled));
        properties.setProperty(TRADE_CHAT_MUTED_KEY, Boolean.toString(tradeChatMuted));
        properties.setProperty(SERVER_MESSAGES_MUTED_KEY, Boolean.toString(serverMessagesMuted));
        properties.setProperty(GREETER_ENABLED_KEY, Boolean.toString(greeterEnabled));
        properties.setProperty(ALLOWED_SERVERS_KEY, String.join(",", allowedServers));
        return properties;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls.properties");
    }
}
