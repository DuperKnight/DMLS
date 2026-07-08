package com.duperknight.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CheckLandsCommand {
    private static final String PREFIX = "§8[§6DMLS - CheckLands§8] §7";
    private static final int LAND_LIST_SLOT = slotIndex(6, 3);
    private static final int PLAYER_LIST_SLOT = slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;
    private static final Pattern FORMATTING_CODE = Pattern.compile("§.");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static CheckSession activeSession;

    private CheckLandsCommand() {
    }

    public static void register() {
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

    private static void start(MinecraftClient client, String ign) {
        if (activeSession != null) {
            activeSession.cancel(client, "Started a new check for §6" + ign + "§7.");
        }

        activeSession = new CheckSession(ign);
        activeSession.start(client);
    }

    private static void handleServerMessage(Text message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(cleanLine(message.getString()));
        }
    }

    private static int slotIndex(int column, int row) {
        return (row - 1) * 9 + (column - 1);
    }

    // DSST left me paranoid with all the issues it had when the staff member left the server while the request was running :(
    private static boolean isNotConnected(MinecraftClient client) {
        return client == null
                || client.player == null
                || client.world == null
                || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen();
    }

    private static void sendCommand(MinecraftClient client, String command) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand(command);
        }
    }

    private static void sendClientMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    private static void closeHandledScreen(MinecraftClient client) {
        if (client != null && client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        }
    }

    private static Optional<ScreenSnapshot> readSlot(MinecraftClient client, int slotIndex, String expectedTitle, int previousSyncId, int waitTicks) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return Optional.empty();
        }

        ScreenHandler handler = handledScreen.getScreenHandler();
        if (handler.slots.size() <= slotIndex) {
            return Optional.empty();
        }

        String title = cleanLine(client.currentScreen.getTitle().getString());
        if (!title.equalsIgnoreCase(expectedTitle)) {
            return Optional.empty();
        }

        if (handler.syncId == previousSyncId && waitTicks < 20) {
            return Optional.empty();
        }

        Slot slot = handler.getSlot(slotIndex);
        if (!slot.hasStack()) {
            return Optional.empty();
        }

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        List<TooltipLine> tooltip = Screen.getTooltipFromItem(client, stack)
                .stream()
                .map(CheckLandsCommand::toTooltipLine)
                .filter(line -> !line.text().isEmpty())
                .toList();

        return Optional.of(new ScreenSnapshot(title, tooltip));
    }

    private static TooltipLine toTooltipLine(Text text) {
        List<TooltipSegment> segments = new ArrayList<>();
        text.visit((style, value) -> {
            String cleaned = cleanSegment(value);
            if (!cleaned.isEmpty()) {
                segments.add(new TooltipSegment(cleaned, style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        StringBuilder plainText = new StringBuilder();
        for (TooltipSegment segment : segments) {
            plainText.append(segment.text());
        }

        return new TooltipLine(plainText.toString().trim(), segments);
    }

    private static String cleanLine(String line) {
        return FORMATTING_CODE.matcher(line).replaceAll("").trim();
    }

    private static String cleanSegment(String line) {
        return FORMATTING_CODE.matcher(line).replaceAll("");
    }

    private static String stripListMarker(String line) {
        String stripped = line.trim();
        if (stripped.startsWith("-")) {
            stripped = stripped.substring(1).trim();
        }
        return stripped;
    }

    private static boolean isTooltipFooter(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("minecraft:") || lower.contains(" component");
    }

    private static Optional<List<String>> parseLands(List<TooltipLine> tooltip) {
        List<String> lands = new ArrayList<>();
        boolean inLands = false;

        for (TooltipLine line : tooltip) {
            String stripped = stripListMarker(line.text());
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (!inLands) {
                inLands = lower.startsWith("lands");
                continue;
            }

            if (isTooltipFooter(stripped)) {
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
            String stripped = stripListMarker(line.text());
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (!inPlayers) {
                inPlayers = lower.startsWith("players");
                continue;
            }

            if (isTooltipFooter(stripped)) {
                continue;
            }

            if (stripped.equals("...")) {
                hasMorePlayers = true;
                continue;
            }

            Optional<String> parsedPlayerName = line.grayUsername().or(() -> lastUsername(stripped));
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

    private static Optional<String> lastUsername(String text) {
        Matcher matcher = USERNAME.matcher(text);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return Optional.ofNullable(lastMatch);
    }

    private record ScreenSnapshot(String title, List<TooltipLine> tooltip) {
    }

    private record TooltipLine(String text, List<TooltipSegment> segments) {
        private Optional<String> grayUsername() {
            for (int i = segments.size() - 1; i >= 0; i--) {
                TooltipSegment segment = segments.get(i);
                if (!segment.isGray()) {
                    continue;
                }

                Optional<String> username = lastUsername(segment.text());
                if (username.isPresent()) {
                    return username;
                }
            }

            return Optional.empty();
        }

        private Optional<String> formattedRoleBefore(String playerName) {
            int playerNameIndex = text.lastIndexOf(playerName);
            if (playerNameIndex < 0) {
                return Optional.empty();
            }

            String role = stripListMarker(text.substring(0, playerNameIndex)).trim();
            if (role.isEmpty()) {
                return Optional.empty();
            }

            Style roleStyle = Style.EMPTY;
            for (TooltipSegment segment : segments) {
                if (segment.isGray() && segment.text().contains(playerName)) {
                    break;
                }
                if (!segment.isGray() && !segment.text().isBlank()) {
                    roleStyle = segment.style();
                }
            }

            return Optional.of(stylePrefix(roleStyle) + role);
        }
    }

    private record TooltipSegment(String text, Style style) {
        private static final TextColor GRAY = TextColor.fromFormatting(Formatting.GRAY);

        private boolean isGray() {
            return GRAY.equals(style.getColor());
        }
    }

    private static String stylePrefix(Style style) {
        StringBuilder prefix = new StringBuilder();
        if (style.getColor() != null) {
            for (Formatting formatting : Formatting.values()) {
                if (formatting.isColor() && style.getColor().equals(TextColor.fromFormatting(formatting))) {
                    prefix.append('§').append(formatting.getCode());
                    break;
                }
            }
        }
        if (style.isBold()) {
            prefix.append("§l");
        }
        if (style.isItalic()) {
            prefix.append("§o");
        }
        if (style.isUnderlined()) {
            prefix.append("§n");
        }
        if (style.isStrikethrough()) {
            prefix.append("§m");
        }
        if (style.isObfuscated()) {
            prefix.append("§k");
        }
        return prefix.toString();
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

    private static final class CheckSession {
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
            sendClientMessage(client, PREFIX + "Checking lands for §6" + ign + "§7...");
            sendTrackedCommand(client, "la player " + ign);
            stage = Stage.WAITING_FOR_LANDS;
        }

        private void tick(MinecraftClient client) {
            if (isNotConnected(client)) {
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
            Optional<ScreenSnapshot> snapshot = readSlot(client, LAND_LIST_SLOT, expectedTitle, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return;
            }

            Optional<List<String>> parsedLands = parseLands(snapshot.get().tooltip());
            if (parsedLands.isEmpty()) {
                return;
            }

            List<String> lands = parsedLands.get();
            if (lands.isEmpty()) {
                sendClientMessage(client, PREFIX + "§6" + ign + "§7 is not in any lands.");
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
            Optional<ScreenSnapshot> snapshot = readSlot(client, PLAYER_LIST_SLOT, currentClaim, previousSyncId, waitTicks);
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
            previousSyncId = currentSyncId(client);
            waitTicks = 0;
            sendCommand(client, command);
        }

        private int currentSyncId(MinecraftClient client) {
            if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
                return handledScreen.getScreenHandler().syncId;
            }
            return -1;
        }

        private void report(MinecraftClient client) {
            String header = PREFIX + "Player §6" + ign + "§7 ";
            sendClientMessage(client, header + separatorForChatWidth(client, header));
            sendClientMessage(client, "§4§lOwner§r§7: " + formatClaims(ownedClaims));
            sendClientMessage(client, "§c§lAdmin§r§7: " + formatClaimResults(adminClaims));
            customRankClaims.stream()
                    .sorted(Comparator.comparingInt((RankClaims rank) -> rank.position).thenComparing(rank -> rank.rank, String.CASE_INSENSITIVE_ORDER))
                    .forEach(rank -> sendClientMessage(client, rank.formattedRank + "§r§7 (" + ordinal(rank.position) + " position): " + formatClaimResults(rank.claims)));
            sendClientMessage(client, "§e§lMember/Unknown§r§7: " + formatClaims(memberOrUnknownClaims));
            sendClientMessage(client, "§7" + separatorForChatWidth(client, ""));
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

        private String separatorForChatWidth(MinecraftClient client, String linePrefix) {
            int chatWidth = ChatHud.getWidth(client.options.getChatWidth().getValue());
            int prefixWidth = client.textRenderer.getWidth(FORMATTING_CODE.matcher(linePrefix).replaceAll(""));
            int dashWidth = Math.max(1, client.textRenderer.getWidth("-"));
            int dashCount = Math.max(3, (chatWidth - prefixWidth) / dashWidth);
            return "-".repeat(dashCount);
        }

        private String timeoutMessage() {
            if (stage == Stage.WAITING_FOR_INFO && currentClaim != null) {
                return "Timed out waiting for §6/la info " + currentClaim + "§7. Stopping.";
            }
            return "Timed out waiting for §6/la player " + ign + "§7. Stopping.";
        }

        private void fail(MinecraftClient client, String reason) {
            sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void cancel(MinecraftClient client, String reason) {
            sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void finish(MinecraftClient client) {
            closeHandledScreen(client);
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
