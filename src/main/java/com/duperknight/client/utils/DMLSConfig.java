package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.modules.DepartmentRank;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.modules.StaffDepartment;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.moderation.ModerationPreferences;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
    private static final String MOD_LOCAL_KEY = "moderation.includeLocal";
    private static final String MOD_TRADE_KEY = "moderation.includeTrade";
    private static final String MOD_RP_KEY = "moderation.includeRp";
    private static final String MOD_STAFF_KEY = "moderation.includeStaff";
    private static final String MOD_ADMIN_KEY = "moderation.includeAdmin";
    private static final String MOD_SERVER_KEY = "moderation.includeServer";
    private static final String MOD_TIMESTAMPS_KEY = "moderation.showTimestamps";
    private static final String MOD_HIGHLIGHTS_KEY = "moderation.highlightAlerts";
    private static final String MOD_RULE_PREFIX = "moderation.rule.";
    private static final String MOD_RULE_SUFFIX = ".enabled";
    private static final String MINI_ME_DUPEY_HUD_KEY = "miniMe.dupeyHud";
    private static final String MINI_ME_SIAFFY_HUD_KEY = "miniMe.siaffyHud";
    private static final String MINI_ME_BEANY_HUD_KEY = "miniMe.beanyHud";
    private static final String MINI_ME_MORVY_HUD_KEY = "miniMe.morvyHud";
    private static final String MINI_ME_BIGGY_HUD_KEY = "miniMe.biggyHud";
    private static final String MINI_ME_CHAOS_MODE_KEY = "miniMe.chaosMode";
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
    private static ModerationPreferences moderationPreferences = ModerationPreferences.defaults();
    private static final Map<String, Boolean> moderationRuleOverrides = new HashMap<>();
    private static MiniMeHudPreferences miniMeHudPreferences = MiniMeHudPreferences.defaults();
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
        refreshCommandCompletionsIfAvailable();
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
        refreshCommandCompletionsIfAvailable();
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
        if (save()) {
            refreshCommandCompletionsIfAvailable();
            return true;
        }
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

    public static ModerationPreferences moderationPreferences() {
        ensureLoaded();
        return moderationPreferences;
    }

    public static boolean setModerationPreferences(ModerationPreferences preferences) {
        ensureLoaded();
        if (preferences == null) return false;
        ModerationPreferences previous = moderationPreferences;
        moderationPreferences = preferences;
        if (save()) return true;
        moderationPreferences = previous;
        return false;
    }

    /** Returns a persisted per-rule override, falling back to the bundled definition's default. */
    public static boolean automaticRuleEnabled(String ruleId, boolean defaultEnabled) {
        ensureLoaded();
        return moderationRuleOverrides.getOrDefault(ruleId, defaultEnabled);
    }

    /** Persists a staff member's enablement choice for one validated automatic rule id. */
    public static boolean setAutomaticRuleEnabled(String ruleId, boolean enabled) {
        ensureLoaded();
        Boolean previous = moderationRuleOverrides.put(ruleId, enabled);
        if (save()) return true;
        if (previous == null) {
            moderationRuleOverrides.remove(ruleId);
        } else {
            moderationRuleOverrides.put(ruleId, previous);
        }
        return false;
    }

    public static MiniMeHudPreferences miniMeHudPreferences() {
        ensureLoaded();
        return miniMeHudPreferences;
    }

    public static boolean setMiniMeHudPreferences(MiniMeHudPreferences preferences) {
        ensureLoaded();
        if (preferences == null) return false;
        MiniMeHudPreferences previous = miniMeHudPreferences;
        miniMeHudPreferences = preferences;
        if (save()) return true;
        miniMeHudPreferences = previous;
        return false;
    }

    /** While enabled, DMLS previews each action without sending it. Not persisted. */
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
        allowedServers = withMandatoryAllowedServers(servers);
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
        ModerationPreferences defaults = ModerationPreferences.defaults();
        moderationPreferences = new ModerationPreferences(
                Boolean.parseBoolean(properties.getProperty(MOD_LOCAL_KEY, Boolean.toString(defaults.includeLocal()))),
                Boolean.parseBoolean(properties.getProperty(MOD_TRADE_KEY, Boolean.toString(defaults.includeTrade()))),
                Boolean.parseBoolean(properties.getProperty(MOD_RP_KEY, Boolean.toString(defaults.includeRp()))),
                Boolean.parseBoolean(properties.getProperty(MOD_STAFF_KEY, Boolean.toString(defaults.includeStaff()))),
                Boolean.parseBoolean(properties.getProperty(MOD_ADMIN_KEY, Boolean.toString(defaults.includeAdmin()))),
                Boolean.parseBoolean(properties.getProperty(MOD_SERVER_KEY, Boolean.toString(defaults.includeServer()))),
                Boolean.parseBoolean(properties.getProperty(MOD_TIMESTAMPS_KEY, Boolean.toString(defaults.showTimestamps()))),
                Boolean.parseBoolean(properties.getProperty(MOD_HIGHLIGHTS_KEY, Boolean.toString(defaults.highlightAlerts())))
        );
        moderationRuleOverrides.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(MOD_RULE_PREFIX) || !key.endsWith(MOD_RULE_SUFFIX)) continue;
            String ruleId = key.substring(MOD_RULE_PREFIX.length(), key.length() - MOD_RULE_SUFFIX.length());
            String value = properties.getProperty(key, "").trim();
            if (ruleId.isEmpty() || !(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
                DMLS.LOGGER.warn("Ignoring invalid automatic moderation preference {}={}", key, value);
                continue;
            }
            moderationRuleOverrides.put(ruleId, Boolean.parseBoolean(value));
        }
        MiniMeHudPreferences miniMeDefaults = MiniMeHudPreferences.defaults();
        miniMeHudPreferences = new MiniMeHudPreferences(
                Boolean.parseBoolean(properties.getProperty(MINI_ME_DUPEY_HUD_KEY, Boolean.toString(miniMeDefaults.dupeyHud()))),
                Boolean.parseBoolean(properties.getProperty(MINI_ME_SIAFFY_HUD_KEY, Boolean.toString(miniMeDefaults.siaffyHud()))),
                Boolean.parseBoolean(properties.getProperty(MINI_ME_BEANY_HUD_KEY, Boolean.toString(miniMeDefaults.beanyHud()))),
                Boolean.parseBoolean(properties.getProperty(MINI_ME_MORVY_HUD_KEY, Boolean.toString(miniMeDefaults.morvyHud()))),
                Boolean.parseBoolean(properties.getProperty(MINI_ME_BIGGY_HUD_KEY, Boolean.toString(miniMeDefaults.biggyHud()))),
                Boolean.parseBoolean(properties.getProperty(MINI_ME_CHAOS_MODE_KEY, Boolean.toString(miniMeDefaults.chaosMode())))
        );
        if (properties.containsKey(ALLOWED_SERVERS_KEY)) {
            allowedServers = withMandatoryAllowedServers(java.util.Arrays.asList(
                    properties.getProperty(ALLOWED_SERVERS_KEY, "").split(",")));
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
        properties.setProperty(MOD_LOCAL_KEY, Boolean.toString(moderationPreferences.includeLocal()));
        properties.setProperty(MOD_TRADE_KEY, Boolean.toString(moderationPreferences.includeTrade()));
        properties.setProperty(MOD_RP_KEY, Boolean.toString(moderationPreferences.includeRp()));
        properties.setProperty(MOD_STAFF_KEY, Boolean.toString(moderationPreferences.includeStaff()));
        properties.setProperty(MOD_ADMIN_KEY, Boolean.toString(moderationPreferences.includeAdmin()));
        properties.setProperty(MOD_SERVER_KEY, Boolean.toString(moderationPreferences.includeServer()));
        properties.setProperty(MOD_TIMESTAMPS_KEY, Boolean.toString(moderationPreferences.showTimestamps()));
        properties.setProperty(MOD_HIGHLIGHTS_KEY, Boolean.toString(moderationPreferences.highlightAlerts()));
        moderationRuleOverrides.forEach((ruleId, enabled) -> properties.setProperty(
                MOD_RULE_PREFIX + ruleId + MOD_RULE_SUFFIX, Boolean.toString(enabled)));
        properties.setProperty(MINI_ME_DUPEY_HUD_KEY, Boolean.toString(miniMeHudPreferences.dupeyHud()));
        properties.setProperty(MINI_ME_SIAFFY_HUD_KEY, Boolean.toString(miniMeHudPreferences.siaffyHud()));
        properties.setProperty(MINI_ME_BEANY_HUD_KEY, Boolean.toString(miniMeHudPreferences.beanyHud()));
        properties.setProperty(MINI_ME_MORVY_HUD_KEY, Boolean.toString(miniMeHudPreferences.morvyHud()));
        properties.setProperty(MINI_ME_BIGGY_HUD_KEY, Boolean.toString(miniMeHudPreferences.biggyHud()));
        properties.setProperty(MINI_ME_CHAOS_MODE_KEY, Boolean.toString(miniMeHudPreferences.chaosMode()));
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

    private static List<String> withMandatoryAllowedServers(List<String> servers) {
        List<String> configured = servers == null ? List.of() : servers;
        return java.util.stream.Stream.concat(ServerGuard.DEFAULT_ALLOWED_SERVERS.stream(), configured.stream())
                .map(ServerGuard::normalizeRule)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    /** Rebuilds the merged server/client command tree after a live access requirement changes. */
    private static void refreshCommandCompletionsIfAvailable() {
        try {
            ClientCommandManager.refreshCommandCompletions();
        } catch (IllegalStateException ignored) {
            // Normal while disconnected or before the server's command packet arrives.
        }
    }
}
