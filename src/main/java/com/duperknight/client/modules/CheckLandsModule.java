package com.duperknight.client.modules;

import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ScreenUtils;
import com.duperknight.client.utils.ScreenUtils.ScreenSnapshot;
import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

public final class CheckLandsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - CheckLands§8] §7";
    private static final int LAND_LIST_SLOT = ScreenUtils.slotIndex(6, 3);
    private static final int PLAYER_LIST_SLOT = ScreenUtils.slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private CheckSession activeSession;

    public CheckLandsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("checklands")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(context -> {
                                    start(context.getSource().getClient(), StringArgumentType.getString(context, "ign"));
                                    return 1;
                                }))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleServerMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> handleServerMessage(message));
    }

    private void start(MinecraftClient client, String ign) {
        if (activeSession != null) {
            activeSession.cancel(client, "Started a new check for §6" + ign + "§7.");
        }

        activeSession = new CheckSession(ign);
        activeSession.start(client);
    }

    private void handleServerMessage(Text message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(ChatUtils.cleanLine(message.getString()));
        }
    }

    private static Optional<List<String>> parseLands(List<TooltipLine> tooltip) {
        List<String> lands = new ArrayList<>();
        boolean inLands = false;

        for (TooltipLine line : tooltip) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (!inLands) {
                inLands = lower.startsWith("lands");
                continue;
            }

            if (TooltipUtils.isTooltipFooter(stripped)) {
                continue;
            }

            if (stripped.equalsIgnoreCase("none")) {
                return Optional.of(List.of());
            }

            if (!stripped.isEmpty()) {
                lands.add(stripped);
            }
        }

        return inLands && !lands.isEmpty() ? Optional.of(lands) : Optional.empty();
    }

    private static Optional<RankScan> parsePlayerRank(List<TooltipLine> tooltip, String ign) {
        boolean inPlayers = false;
        int playerPosition = 0;
        List<String> distinctRanks = new ArrayList<>();
        List<RankStats> rankStats = new ArrayList<>();
        String previousRank = "";
        RankAssignment targetRank = null;
        boolean hasMorePlayers = false;

        for (TooltipLine line : tooltip) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (!inPlayers) {
                inPlayers = lower.startsWith("players");
                continue;
            }

            if (TooltipUtils.isTooltipFooter(stripped)) {
                continue;
            }

            if (stripped.equals("...")) {
                hasMorePlayers = true;
                continue;
            }

            Optional<String> parsedPlayerName = line.grayUsername(USERNAME).or(() -> TooltipUtils.lastMatch(stripped, USERNAME));
            if (parsedPlayerName.isEmpty()) {
                continue;
            }

            playerPosition++;
            String playerName = parsedPlayerName.get();
            String role = stripped.substring(0, Math.max(0, stripped.lastIndexOf(playerName))).trim();
            String rankName = rankName(playerPosition, role);
            String formattedRankName = formattedRankName(playerPosition, line, playerName, rankName);
            addDistinctRank(distinctRanks, rankName);
            int position = rankPosition(distinctRanks, rankName);
            RankStats stats = getOrCreateRankStats(rankStats, rankName, formattedRankName, position);
            stats.visibleCount++;
            if (!previousRank.isEmpty() && !previousRank.equalsIgnoreCase(rankName)) {
                setOpenEnded(rankStats, previousRank, false);
            }
            previousRank = rankName;

            if (!playerName.equalsIgnoreCase(ign)) {
                continue;
            }

            if (playerPosition == 1) {
                targetRank = RankAssignment.owner();
                continue;
            }

            String normalizedRole = role.toLowerCase(Locale.ROOT);
            if (normalizedRole.contains("admin")) {
                targetRank = RankAssignment.admin();
                continue;
            }
            if (normalizedRole.equals("member")) {
                targetRank = RankAssignment.memberOrUnknown();
                continue;
            }

            targetRank = RankAssignment.custom(rankName, formattedRankName, position);
        }

        if (!previousRank.isEmpty()) {
            setOpenEnded(rankStats, previousRank, hasMorePlayers);
        }

        if (!inPlayers) {
            return Optional.empty();
        }

        return Optional.of(new RankScan(targetRank == null ? RankAssignment.memberOrUnknown() : targetRank, rankStats));
    }

    private static String formattedRankName(int playerPosition, TooltipLine line, String playerName, String fallbackRank) {
        if (playerPosition == 1 || fallbackRank.equals("Admin") || fallbackRank.equals("Member/Unknown")) {
            return fallbackRank;
        }
        return line.formattedRoleBefore(playerName).orElse(fallbackRank);
    }

    private static String rankName(int playerPosition, String role) {
        if (playerPosition == 1) {
            return "Owner";
        }
        if (role.toLowerCase(Locale.ROOT).contains("admin")) {
            return "Admin";
        }
        return role.isEmpty() ? "Member/Unknown" : role;
    }

    private static void addDistinctRank(List<String> ranks, String rank) {
        for (String existingRank : ranks) {
            if (existingRank.equalsIgnoreCase(rank)) {
                return;
            }
        }
        ranks.add(rank);
    }

    private static int rankPosition(List<String> ranks, String rank) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).equalsIgnoreCase(rank)) {
                return i + 1;
            }
        }
        return ranks.size() + 1;
    }

    private static RankStats getOrCreateRankStats(List<RankStats> rankStats, String rank, String formattedRank, int position) {
        for (RankStats stats : rankStats) {
            if (stats.rank.equalsIgnoreCase(rank) && stats.position == position) {
                return stats;
            }
        }

        RankStats stats = new RankStats(rank, formattedRank, position);
        rankStats.add(stats);
        return stats;
    }

    private static void setOpenEnded(List<RankStats> rankStats, String rank, boolean openEnded) {
        for (RankStats stats : rankStats) {
            if (stats.rank.equalsIgnoreCase(rank)) {
                stats.openEnded = openEnded;
            }
        }
    }

    private enum Stage {
        WAITING_FOR_LANDS,
        SENDING_NEXT_INFO_COMMAND,
        WAITING_FOR_INFO
    }

    private enum RankType {
        OWNER,
        ADMIN,
        CUSTOM,
        MEMBER_OR_UNKNOWN
    }

    private record RankScan(RankAssignment assignment, List<RankStats> stats) {
    }

    private static final class RankStats {
        private final String rank;
        private final String formattedRank;
        private final int position;
        private int visibleCount;
        private boolean openEnded;

        private RankStats(String rank, String formattedRank, int position) {
            this.rank = rank;
            this.formattedRank = formattedRank;
            this.position = position;
        }
    }

    private record RankAssignment(RankType type, String customRank, String formattedCustomRank, int position) {
        private static RankAssignment owner() {
            return new RankAssignment(RankType.OWNER, "", "", 1);
        }

        private static RankAssignment admin() {
            return new RankAssignment(RankType.ADMIN, "", "", Integer.MAX_VALUE);
        }

        private static RankAssignment custom(String customRank, String formattedCustomRank, int position) {
            return new RankAssignment(RankType.CUSTOM, customRank, formattedCustomRank, position);
        }

        private static RankAssignment memberOrUnknown() {
            return new RankAssignment(RankType.MEMBER_OR_UNKNOWN, "", "", Integer.MAX_VALUE);
        }
    }

    private static final class RankClaims {
        private final String rank;
        private String formattedRank;
        private final int position;
        private final List<ClaimResult> claims = new ArrayList<>();

        private RankClaims(String rank, String formattedRank, int position) {
            this.rank = rank;
            this.formattedRank = formattedRank;
            this.position = position;
        }

        private void addClaim(String claim, Optional<RankStats> stats) {
            claims.add(toClaimResult(claim, stats));
            stats.ifPresent(rankStats -> {
                if (!rankStats.formattedRank.isBlank()) {
                    formattedRank = rankStats.formattedRank;
                }
            });
        }
    }

    private record ClaimResult(String claim, int visibleCount, boolean openEnded) {
    }

    private static ClaimResult toClaimResult(String claim, Optional<RankStats> stats) {
        return stats
                .map(rankStats -> new ClaimResult(claim, Math.max(1, rankStats.visibleCount), rankStats.openEnded))
                .orElseGet(() -> new ClaimResult(claim, 1, false));
    }

    private final class CheckSession {
        private final String ign;
        private final Queue<String> remainingClaims = new ArrayDeque<>();
        private final List<String> ownedClaims = new ArrayList<>();
        private final List<ClaimResult> adminClaims = new ArrayList<>();
        private final List<RankClaims> customRankClaims = new ArrayList<>();
        private final List<String> memberOrUnknownClaims = new ArrayList<>();

        private Stage stage = Stage.WAITING_FOR_LANDS;
        private String currentClaim;
        private int previousSyncId = -1;
        private int waitTicks;

        private CheckSession(String ign) {
            this.ign = ign;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Checking lands for §6" + ign + "§7...");
            sendTrackedCommand(client, "la player " + ign);
            stage = Stage.WAITING_FOR_LANDS;
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            if (waitTicks > MENU_TIMEOUT_TICKS) {
                fail(client, timeoutMessage());
                return;
            }

            switch (stage) {
                case WAITING_FOR_LANDS -> waitForLands(client);
                case SENDING_NEXT_INFO_COMMAND -> sendNextInfoCommand(client);
                case WAITING_FOR_INFO -> waitForInfo(client);
            }
        }

        private void handleServerMessage(String message) {
            String lowerMessage = message.toLowerCase(Locale.ROOT);
            if (lowerMessage.startsWith("lands:")
                    && lowerMessage.contains("no player with the name")
                    && lowerMessage.contains(ign.toLowerCase(Locale.ROOT))) {
                activeSession = null;
            }
        }

        private void waitForLands(MinecraftClient client) {
            String expectedTitle = "Player " + ign;
            Optional<ScreenSnapshot> snapshot = ScreenUtils.readSlot(client, LAND_LIST_SLOT, expectedTitle, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return;
            }

            Optional<List<String>> parsedLands = parseLands(snapshot.get().tooltip());
            if (parsedLands.isEmpty()) {
                return;
            }

            List<String> lands = parsedLands.get();
            if (lands.isEmpty()) {
                ChatUtils.sendClientMessage(client, PREFIX + "§6" + ign + "§7 is not in any lands.");
                finish(client);
                return;
            }

            remainingClaims.addAll(lands);
            stage = Stage.SENDING_NEXT_INFO_COMMAND;
            waitTicks = 0;
        }

        private void sendNextInfoCommand(MinecraftClient client) {
            currentClaim = remainingClaims.poll();
            if (currentClaim == null) {
                report(client);
                finish(client);
                return;
            }

            sendTrackedCommand(client, "la info " + currentClaim);
            stage = Stage.WAITING_FOR_INFO;
        }

        private void waitForInfo(MinecraftClient client) {
            Optional<ScreenSnapshot> snapshot = ScreenUtils.readSlot(client, PLAYER_LIST_SLOT, currentClaim, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return;
            }

            Optional<RankScan> parsedRank = parsePlayerRank(snapshot.get().tooltip(), ign);
            if (parsedRank.isEmpty()) {
                return;
            }

            RankScan scan = parsedRank.get();
            RankAssignment rank = scan.assignment();
            switch (rank.type()) {
                case OWNER -> ownedClaims.add(currentClaim);
                case ADMIN -> {
                    adminClaims.add(toClaimResult(currentClaim, findRankStats(scan.stats(), "Admin", Integer.MAX_VALUE)));
                }
                case CUSTOM -> addCustomRankClaim(rank, findRankStats(scan.stats(), rank.customRank(), rank.position()), currentClaim);
                case MEMBER_OR_UNKNOWN -> memberOrUnknownClaims.add(currentClaim);
            }

            stage = Stage.SENDING_NEXT_INFO_COMMAND;
            waitTicks = 0;
        }

        private void sendTrackedCommand(MinecraftClient client, String command) {
            previousSyncId = ScreenUtils.currentSyncId(client);
            waitTicks = 0;
            ClientUtils.sendCommand(client, command);
        }

        private void report(MinecraftClient client) {
            String header = PREFIX + "Player §6" + ign + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            ChatUtils.sendClientMessage(client, "§4§lOwner§r§7: " + formatClaims(ownedClaims));
            ChatUtils.sendClientMessage(client, "§c§lAdmin§r§7: " + formatClaimResults(adminClaims));
            customRankClaims.stream()
                    .sorted(Comparator.comparingInt((RankClaims rank) -> rank.position).thenComparing(rank -> rank.rank, String.CASE_INSENSITIVE_ORDER))
                    .forEach(rank -> ChatUtils.sendClientMessage(client, rank.formattedRank + "§r§7 (" + ordinal(rank.position) + " rank): " + formatClaimResults(rank.claims)));
            ChatUtils.sendClientMessage(client, "§e§lMember/Unknown§r§7: " + formatClaims(memberOrUnknownClaims));
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void addCustomRankClaim(RankAssignment rank, Optional<RankStats> stats, String claim) {
            for (RankClaims existingRank : customRankClaims) {
                if (existingRank.rank.equalsIgnoreCase(rank.customRank()) && existingRank.position == rank.position()) {
                    existingRank.addClaim(claim, stats);
                    return;
                }
            }

            RankClaims claims = new RankClaims(rank.customRank(), rank.formattedCustomRank(), rank.position());
            claims.addClaim(claim, stats);
            customRankClaims.add(claims);
        }

        private String formatClaims(List<String> claims) {
            return claims.isEmpty() ? "None" : String.join(", ", claims);
        }

        private String formatClaimResults(List<ClaimResult> claims) {
            if (claims.isEmpty()) {
                return "None";
            }

            List<String> formattedClaims = new ArrayList<>();
            for (ClaimResult claim : claims) {
                formattedClaims.add(claim.claim() + " " + countSuffix(claim));
            }
            return String.join(", ", formattedClaims);
        }

        private Optional<RankStats> findRankStats(List<RankStats> stats, String rank, int position) {
            for (RankStats rankStats : stats) {
                if (rankStats.rank.equalsIgnoreCase(rank)
                        && (position == Integer.MAX_VALUE || rankStats.position == position)) {
                    return Optional.of(rankStats);
                }
            }
            return Optional.empty();
        }

        private String countSuffix(ClaimResult claim) {
            return "(1/" + claim.visibleCount() + (claim.openEnded() ? "+" : "") + ")";
        }

        private String ordinal(int number) {
            int lastTwoDigits = number % 100;
            if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
                return number + "th";
            }

            return switch (number % 10) {
                case 1 -> number + "st";
                case 2 -> number + "nd";
                case 3 -> number + "rd";
                default -> number + "th";
            };
        }

        private String timeoutMessage() {
            if (stage == Stage.WAITING_FOR_INFO && currentClaim != null) {
                return "Timed out waiting for §6/la info " + currentClaim + "§7. Stopping.";
            }
            return "Timed out waiting for §6/la player " + ign + "§7. Stopping.";
        }

        private void fail(MinecraftClient client, String reason) {
            ChatUtils.sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void cancel(MinecraftClient client, String reason) {
            ChatUtils.sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void finish(MinecraftClient client) {
            ScreenUtils.closeHandledScreen(client);
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
