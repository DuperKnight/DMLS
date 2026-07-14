package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.modules.DepartmentRank;
import com.duperknight.client.modules.StaffDepartment;
import com.duperknight.client.modules.StaffRank;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Properties;

/**
 * Handles durable DMLS preferences, stored in config/dmls.properties.
 */
public final class DMLSConfig {
    private static final String RANK_KEY = "staffRank";
    private static final String BANS_RANK_KEY = "bansRank";
    private static final String EVENTS_RANK_KEY = "eventsRank";
    private static final String WAR_RANK_KEY = "warRank";
    private static final String ALERTS_KEY = "chatAlerts";
    private static final String TRADE_CHAT_MUTED_KEY = "tradeChatMuted";
    private static final String SERVER_MESSAGES_MUTED_KEY = "serverMessagesMuted";
    private static final String GREETER_ENABLED_KEY = "greeterEnabled";
    private static final String ALLOWED_SERVERS_KEY = "allowedServers";
    private static final StaffRank DEFAULT_RANK = StaffRank.NONE;

    private static boolean loaded;
    private static StaffRank staffRank = DEFAULT_RANK;
    private static StaffRank persistedStaffRank = DEFAULT_RANK;
    private static boolean storedStaffRankPresent;
    private static final Map<StaffDepartment, DepartmentRank> departmentRanks = defaultDepartmentRanks();
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

    /** Returns the last rank stored in config, even while live access is temporarily locked for HUB verification. */
    public static StaffRank storedStaffRank() {
        ensureLoaded();
        return persistedStaffRank;
    }

    /** Returns whether the loaded config contained a previously recognized staff rank. */
    public static boolean hasStoredStaffRank() {
        ensureLoaded();
        return storedStaffRankPresent;
    }

    /** Locks rank-gated features while the hub scoreboard is loading without overwriting the previous rank on disk. */
    public static void clearStaffRankForHubVerification() {
        ensureLoaded();
        staffRank = StaffRank.NONE;
    }

    /**
     * Applies a rank read from the hub. The in-memory access state is never rolled back if persistence fails;
     * showing the detected access level safely is more important than retaining a stale value.
     */
    public static boolean setDetectedStaffRank(StaffRank rank) {
        ensureLoaded();
        staffRank = rank;
        persistedStaffRank = rank;
        boolean saved = save();
        if (saved) {
            storedStaffRankPresent = rank.isStaff();
        }
        return saved;
    }

    public static boolean hasRecognizedStaffRank() {
        return staffRank().isStaff();
    }

    public static DepartmentRank departmentRank(StaffDepartment department) {
        ensureLoaded();
        return departmentRanks.getOrDefault(department, DepartmentRank.NOT_ASSIGNED);
    }

    public static boolean setDepartmentRank(StaffDepartment department, DepartmentRank rank) {
        ensureLoaded();
        if (!rank.belongsTo(department)) {
            return false;
        }
        DepartmentRank previous = departmentRanks.put(department, rank);
        if (save()) return true;
        departmentRanks.put(department, previous);
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
     * Parses a staff rank from persisted or scoreboard text, also accepting aliases like "mod" and "srmod".
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
        persistedStaffRank = staffRank;
        storedStaffRankPresent = properties.containsKey(RANK_KEY) && staffRank.isStaff();
        departmentRanks.put(StaffDepartment.BANS, parseDepartmentRank(
                properties.getProperty(BANS_RANK_KEY, ""), StaffDepartment.BANS));
        departmentRanks.put(StaffDepartment.EVENTS, parseDepartmentRank(
                properties.getProperty(EVENTS_RANK_KEY, ""), StaffDepartment.EVENTS));
        departmentRanks.put(StaffDepartment.WAR, parseDepartmentRank(
                properties.getProperty(WAR_RANK_KEY, ""), StaffDepartment.WAR));
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
        properties.setProperty(RANK_KEY, persistedStaffRank.name());
        properties.setProperty(BANS_RANK_KEY, departmentRanks.get(StaffDepartment.BANS).name());
        properties.setProperty(EVENTS_RANK_KEY, departmentRanks.get(StaffDepartment.EVENTS).name());
        properties.setProperty(WAR_RANK_KEY, departmentRanks.get(StaffDepartment.WAR).name());
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

    private static DepartmentRank parseDepartmentRank(String input, StaffDepartment department) {
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            DepartmentRank rank = DepartmentRank.valueOf(normalized);
            return rank.belongsTo(department) ? rank : DepartmentRank.NOT_ASSIGNED;
        } catch (IllegalArgumentException exception) {
            return DepartmentRank.NOT_ASSIGNED;
        }
    }

    private static Map<StaffDepartment, DepartmentRank> defaultDepartmentRanks() {
        Map<StaffDepartment, DepartmentRank> ranks = new EnumMap<>(StaffDepartment.class);
        for (StaffDepartment department : StaffDepartment.values()) {
            ranks.put(department, DepartmentRank.NOT_ASSIGNED);
        }
        return ranks;
    }
}
