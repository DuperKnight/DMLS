package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.DMLSClient;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.modules.StaffRank;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Stoneworks hub sidebar and persists the server-reported staff rank. */
public final class HubStaffRankDetector {
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final Pattern RANK_LINE = Pattern.compile("(?i)^rank\\s*:\\s*(.*)$");
    private static final Pattern LOBBY_LINE = Pattern.compile("(?i)^current\\s+lobby\\s*:\\s*(.*)$");
    private static final Comparator<PlayerScoreEntry> DISPLAY_ORDER = Comparator
            .comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static int ticksUntilCheck;
    private static String lastAppliedSignature = "";
    private static boolean stoneworksSessionInitialized;
    private static StaffRank comparisonRank = StaffRank.NONE;
    private static boolean comparisonRankPresent;
    private static PendingAnnouncement pendingAnnouncement;

    private HubStaffRankDetector() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ticksUntilCheck = 0;
            if (isStoneworks(client) && !stoneworksSessionInitialized) {
                lastAppliedSignature = "";
                initializeStoneworksSession();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ticksUntilCheck = 0;
            lastAppliedSignature = "";
            stoneworksSessionInitialized = false;
            pendingAnnouncement = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(HubStaffRankDetector::tick);
    }

    private static void tick(Minecraft client) {
        openPendingAnnouncement(client);
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        if (!isStoneworks(client)) return;
        if (!stoneworksSessionInitialized) {
            initializeStoneworksSession();
        }
        if (client.level == null) return;

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;

        List<String> lines = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(DISPLAY_ORDER)
                .map(entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString())
                .toList();
        Detection detection = detect(objective.getDisplayName().getString(), lines);
        if (detection.status == Status.STAFF) {
            StaffRank detectedRank = detection.rank.orElseThrow();
            if (apply(detectedRank, "staff:" + detectedRank.name())) {
                AnnouncementKind kind = announcementKind(comparisonRankPresent, comparisonRank, detectedRank);
                if (kind != AnnouncementKind.NONE) {
                    pendingAnnouncement = new PendingAnnouncement(kind, comparisonRank, detectedRank);
                }
                comparisonRank = detectedRank;
                comparisonRankPresent = true;
            }
        } else if (detection.status == Status.NON_STAFF) {
            apply(StaffRank.NONE, "non_staff:" + detection.rawRank.toLowerCase(Locale.ROOT));
        }
    }

    private static void initializeStoneworksSession() {
        stoneworksSessionInitialized = true;
        comparisonRank = DMLSConfig.storedStaffRank();
        comparisonRankPresent = DMLSConfig.hasStoredStaffRank();
        // Never let a staff rank cached from an older session grant menu access while the hub loads.
        DMLSConfig.clearStaffRankForHubVerification();
        lastAppliedSignature = "pending";
    }

    private static boolean apply(StaffRank rank, String signature) {
        if (lastAppliedSignature.equals(signature)) return false;
        lastAppliedSignature = signature;
        if (!DMLSConfig.setDetectedStaffRank(rank)) {
            DMLS.LOGGER.warn("Detected hub staff rank {} but could not persist it", rank);
        }
        return true;
    }

    private static void openPendingAnnouncement(Minecraft client) {
        if (pendingAnnouncement == null || client == null || client.level == null
                || client.gui.screen() != null || !DMLSConfig.hasRecognizedStaffRank()) {
            return;
        }
        PendingAnnouncement announcement = pendingAnnouncement;
        pendingAnnouncement = null;
        client.gui.setScreen(announcement.kind == AnnouncementKind.WELCOME
                ? DMLSHomeScreen.welcome(DMLSClient.modules(), announcement.currentRank)
                : DMLSHomeScreen.promotion(DMLSClient.modules(), announcement.previousRank, announcement.currentRank));
    }

    public static AnnouncementKind announcementKind(
            boolean previousRankPresent,
            StaffRank previousRank,
            StaffRank currentRank
    ) {
        if (!previousRankPresent || !previousRank.isStaff()) return AnnouncementKind.WELCOME;
        return currentRank.level() > previousRank.level() ? AnnouncementKind.PROMOTION : AnnouncementKind.NONE;
    }

    private static boolean isStoneworks(Minecraft client) {
        return client != null && !client.isLocalServer() && client.getCurrentServer() != null
                && ServerGuard.isAllowed(client.getCurrentServer().ip, ServerGuard.DEFAULT_ALLOWED_SERVERS);
    }

    /** Pure parser kept separate from Minecraft state so scoreboard formats can be regression-tested. */
    public static Detection detect(String objectiveTitle, List<String> rawLines) {
        List<String> lines = rawLines.stream().map(HubStaffRankDetector::normalize).toList();
        if (!isHub(objectiveTitle, lines)) {
            return new Detection(Status.NOT_HUB, Optional.empty(), "");
        }

        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = RANK_LINE.matcher(lines.get(index));
            if (!matcher.matches()) continue;

            String rawRank = matcher.group(1).trim();
            if (rawRank.isEmpty() && index + 1 < lines.size()) {
                rawRank = lines.get(index + 1);
            }
            if (rawRank.isEmpty()) {
                return new Detection(Status.RANK_MISSING, Optional.empty(), "");
            }

            String detectedRank = rawRank;
            Optional<StaffRank> parsed = DMLSConfig.parseRank(detectedRank).filter(StaffRank::isStaff);
            return parsed
                    .map(rank -> new Detection(Status.STAFF, Optional.of(rank), detectedRank))
                    .orElseGet(() -> new Detection(Status.NON_STAFF, Optional.empty(), detectedRank));
        }
        return new Detection(Status.RANK_MISSING, Optional.empty(), "");
    }

    private static boolean isHub(String objectiveTitle, List<String> lines) {
        if (normalize(objectiveTitle).equalsIgnoreCase("hub")) return true;
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = LOBBY_LINE.matcher(lines.get(index));
            if (!matcher.matches()) continue;
            String lobby = matcher.group(1).trim();
            if (lobby.equalsIgnoreCase("hub")) return true;
            if (lobby.isEmpty() && index + 1 < lines.size() && lines.get(index + 1).equalsIgnoreCase("hub")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("§.", "")
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public enum Status {
        NOT_HUB,
        RANK_MISSING,
        STAFF,
        NON_STAFF
    }

    public enum AnnouncementKind {
        NONE,
        WELCOME,
        PROMOTION
    }

    public record Detection(Status status, Optional<StaffRank> rank, String rawRank) {
    }

    private record PendingAnnouncement(AnnouncementKind kind, StaffRank previousRank, StaffRank currentRank) {
    }
}
