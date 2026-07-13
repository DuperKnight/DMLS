package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.CheckLandsScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.LandsMenuParser;
import com.duperknight.client.parser.LandsMenuParser.LandList;
import com.duperknight.client.parser.LandsMenuParser.RankAssignment;
import com.duperknight.client.parser.LandsMenuParser.RankScan;
import com.duperknight.client.parser.LandsMenuParser.RankStats;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.MenuCommandQuery;
import com.duperknight.client.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

public final class CheckLandsModule extends DMLSModule {
    public static final String OPERATION_ID = "check-lands";
    private static final String PREFIX = "§8[§6DMLS - CheckLands§8] §7";
    private static final int LAND_LIST_SLOT = ScreenUtils.slotIndex(6, 3);
    private static final int PLAYER_LIST_SLOT = ScreenUtils.slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;

    private final OperationCoordinator coordinator;
    private CheckSession activeSession;

    public CheckLandsModule() {
        this(OperationCoordinator.global());
    }

    CheckLandsModule(OperationCoordinator coordinator) {
        super(StaffRank.MODERATOR);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.check_lands.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CAMPFIRE);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.check_lands.description.1"),
                Text.translatable("dmls.module.check_lands.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new CheckLandsScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts a batch that owns the shared response tracker until every player is complete. */
    public OperationStartResult submit(MinecraftClient client, String input) {
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;
        List<String> rejected = new ArrayList<>();
        List<String> players = InputValidators.uniqueUsernames(input, rejected);
        if (players.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            return OperationStartResult.INVALID;
        }

        replaceOwnOperation(client);
        if (players.size() > 1) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands.queued",
                    players.size(), String.join(", ", players));
        }
        CheckSession candidate = new CheckSession(players);
        activeSession = candidate;
        OperationStartResult result = coordinator.start(client, OPERATION_ID, "Check Lands", candidate);
        if (result == OperationStartResult.STARTED && !candidate.acceptedAtStart()) {
            result = OperationStartResult.SERVER_BLOCKED;
        }
        if (result != OperationStartResult.STARTED && activeSession == candidate) activeSession = null;
        reportStartFailure(client, result);
        return result;
    }

    private void replaceOwnOperation(MinecraftClient client) {
        if (coordinator.activeDescriptor().filter(descriptor -> descriptor.operationId().equals(OPERATION_ID)).isPresent()) {
            coordinator.cancel(OPERATION_ID, client);
        }
    }

    private void reportStartFailure(MinecraftClient client, OperationStartResult result) {
        if (result == OperationStartResult.BUSY) {
            String owner = coordinator.activeDescriptor().map(descriptor -> descriptor.displayName()).orElse("Another operation");
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
        } else if (result != OperationStartResult.STARTED && result != OperationStartResult.SERVER_BLOCKED
                && result != OperationStartResult.INVALID) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.start_failed");
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
            stats.filter(value -> !value.formattedRank().isBlank())
                    .ifPresent(value -> formattedRank = value.formattedRank());
        }
    }

    private record ClaimResult(String claim, int visibleCount, boolean openEnded) {
    }

    private static ClaimResult toClaimResult(String claim, Optional<RankStats> stats) {
        return stats.map(value -> new ClaimResult(claim, Math.max(1, value.visibleCount()), value.openEnded()))
                .orElseGet(() -> new ClaimResult(claim, 1, false));
    }

    private enum Stage { WAITING_FOR_LANDS, SENDING_NEXT_INFO_COMMAND, WAITING_FOR_INFO }

    private final class CheckSession implements ManagedOperation {
        private final Queue<String> remainingPlayers = new ArrayDeque<>();
        private final Queue<String> remainingClaims = new ArrayDeque<>();
        private final List<String> ownedClaims = new ArrayList<>();
        private final List<ClaimResult> adminClaims = new ArrayList<>();
        private final List<RankClaims> customRankClaims = new ArrayList<>();
        private final List<String> memberOrUnknownClaims = new ArrayList<>();

        private OperationHandle handle;
        private Stage stage;
        private String ign;
        private String currentClaim;
        private MenuCommandQuery activeQuery;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;

        private CheckSession(List<String> players) {
            remainingPlayers.addAll(players);
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            this.handle = handle;
            startNextPlayer(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            switch (stage) {
                case WAITING_FOR_LANDS -> waitForLands(client);
                case SENDING_NEXT_INFO_COMMAND -> sendNextInfoCommand(client);
                case WAITING_FOR_INFO -> waitForInfo(client);
            }
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
            if (stage != Stage.WAITING_FOR_LANDS || message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            String lower = message.cleanText().toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("lands:") && lower.contains("no player with the name")
                    && lower.contains(ign.toLowerCase(java.util.Locale.ROOT))) {
                finishPlayer(client);
            }
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            ScreenUtils.closeHandledScreen(client);
            remainingPlayers.clear();
            if (activeSession == this) activeSession = null;
            if (reason != OperationCancelReason.MODULE_REQUESTED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
            }
        }

        private void startNextPlayer(MinecraftClient client) {
            ign = remainingPlayers.poll();
            if (ign == null) {
                finishAll(client);
                return;
            }
            resetPlayerState();
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands.checking", ign);
            activeQuery = new MenuCommandQuery("la player " + ign, "Player " + ign,
                    MENU_TIMEOUT_TICKS, LAND_LIST_SLOT);
            initialDispatch = activeQuery.start(client, handle::dispatchCommand);
            if (initialDispatch == CommandDispatch.BLOCKED) {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
            }
            stage = Stage.WAITING_FOR_LANDS;
        }

        private boolean acceptedAtStart() {
            return initialDispatch != CommandDispatch.BLOCKED;
        }

        private void resetPlayerState() {
            remainingClaims.clear();
            ownedClaims.clear();
            adminClaims.clear();
            customRankClaims.clear();
            memberOrUnknownClaims.clear();
            currentClaim = null;
        }

        private void waitForLands(MinecraftClient client) {
            MenuCommandQuery.TickResult tick = activeQuery.tick(client);
            if (!handleTerminalQueryStatus(client, tick, "/la player " + ign)) return;
            if (tick.status() != MenuCommandQuery.Status.READY) return;

            LandList parsed = tick.result().flatMap(result -> result.tooltip(LAND_LIST_SLOT))
                    .map(LandsMenuParser::parseLands).orElseGet(() -> LandsMenuParser.parseLands(List.of()));
            if (parsed.status() == LandsMenuParser.ParseStatus.MALFORMED) {
                malformed(client, "/la player " + ign);
                return;
            }
            if (parsed.lands().isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands.none", ign);
                finishPlayer(client);
                return;
            }
            remainingClaims.addAll(parsed.lands());
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    parsed.lands().size() == 1 ? "dmls.chat.check_lands.found.one" : "dmls.chat.check_lands.found.many",
                    parsed.lands().size(), ign);
            stage = Stage.SENDING_NEXT_INFO_COMMAND;
        }

        private void sendNextInfoCommand(MinecraftClient client) {
            currentClaim = remainingClaims.poll();
            if (currentClaim == null) {
                report(client);
                finishPlayer(client);
                return;
            }
            activeQuery = new MenuCommandQuery("la info " + currentClaim, currentClaim,
                    MENU_TIMEOUT_TICKS, PLAYER_LIST_SLOT);
            activeQuery.start(client, handle::dispatchCommand);
            stage = Stage.WAITING_FOR_INFO;
        }

        private void waitForInfo(MinecraftClient client) {
            MenuCommandQuery.TickResult tick = activeQuery.tick(client);
            if (!handleTerminalQueryStatus(client, tick, "/la info " + currentClaim)) return;
            if (tick.status() != MenuCommandQuery.Status.READY) return;

            RankScan scan = tick.result().flatMap(result -> result.tooltip(PLAYER_LIST_SLOT))
                    .map(tooltip -> LandsMenuParser.parsePlayerRank(tooltip, ign))
                    .orElseGet(() -> LandsMenuParser.parsePlayerRank(List.of(), ign));
            if (scan.status() == LandsMenuParser.ParseStatus.MALFORMED) {
                malformed(client, "/la info " + currentClaim);
                return;
            }
            RankAssignment rank = scan.assignment();
            switch (rank.type()) {
                case OWNER -> ownedClaims.add(currentClaim);
                case ADMIN -> adminClaims.add(toClaimResult(currentClaim,
                        findRankStats(scan.stats(), "Admin", Integer.MAX_VALUE)));
                case CUSTOM -> addCustomRankClaim(rank,
                        findRankStats(scan.stats(), rank.customRank(), rank.position()), currentClaim);
                case MEMBER_OR_UNKNOWN -> memberOrUnknownClaims.add(currentClaim);
            }
            stage = Stage.SENDING_NEXT_INFO_COMMAND;
        }

        /** Returns true only when the caller may continue inspecting this tick result. */
        private boolean handleTerminalQueryStatus(MinecraftClient client, MenuCommandQuery.TickResult tick, String command) {
            switch (tick.status()) {
                case WAITING -> { return false; }
                case TIMED_OUT -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.timeout", command);
                    finishAll(client);
                    return false;
                }
                case CANCELLED -> {
                    handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                    return false;
                }
                case SIMULATED -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.simulated", command);
                    finishPlayer(client);
                    return false;
                }
                case READY -> { return true; }
            }
            return false;
        }

        private void malformed(MinecraftClient client, String command) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.malformed", command);
            finishAll(client);
        }

        private void finishPlayer(MinecraftClient client) {
            ScreenUtils.closeHandledScreen(client);
            startNextPlayer(client);
        }

        private void finishAll(MinecraftClient client) {
            ScreenUtils.closeHandledScreen(client);
            remainingPlayers.clear();
            if (activeSession == this) activeSession = null;
            if (handle != null) handle.complete();
        }

        private void report(MinecraftClient client) {
            String header = PREFIX + "Player §6" + ign + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            ChatUtils.sendClientMessage(client, Text.translatable("dmls.chat.check_lands.owner").append(claimsText(ownedClaims)));
            ChatUtils.sendClientMessage(client, Text.translatable("dmls.chat.check_lands.admin").append(claimResultsText(adminClaims)));
            customRankClaims.stream()
                    .sorted(Comparator.comparingInt((RankClaims rank) -> rank.position)
                            .thenComparing(rank -> rank.rank, String.CASE_INSENSITIVE_ORDER))
                    .forEach(rank -> ChatUtils.sendClientMessage(client,
                            Text.literal(rank.formattedRank + "§r§7 (" + ordinal(rank.position)
                                    + " rank): ").append(claimResultsText(rank.claims))));
            ChatUtils.sendClientMessage(client, Text.translatable("dmls.chat.check_lands.member_unknown")
                    .append(claimsText(memberOrUnknownClaims)));
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void addCustomRankClaim(RankAssignment rank, Optional<RankStats> stats, String claim) {
            for (RankClaims existing : customRankClaims) {
                if (existing.rank.equalsIgnoreCase(rank.customRank()) && existing.position == rank.position()) {
                    existing.addClaim(claim, stats);
                    return;
                }
            }
            RankClaims created = new RankClaims(rank.customRank(), rank.formattedCustomRank(), rank.position());
            created.addClaim(claim, stats);
            customRankClaims.add(created);
        }

        private Optional<RankStats> findRankStats(List<RankStats> stats, String rank, int position) {
            return stats.stream().filter(value -> value.rank().equalsIgnoreCase(rank)
                    && (position == Integer.MAX_VALUE || value.position() == position)).findFirst();
        }

        private MutableText claimsText(List<String> claims) {
            if (claims.isEmpty()) return Text.translatable("dmls.chat.common.none");
            MutableText text = Text.literal("");
            for (int i = 0; i < claims.size(); i++) {
                if (i > 0) text.append("§7, ");
                text.append(clickableClaim(claims.get(i)));
            }
            return text;
        }

        private MutableText claimResultsText(List<ClaimResult> claims) {
            if (claims.isEmpty()) return Text.translatable("dmls.chat.common.none");
            MutableText text = Text.literal("");
            for (int i = 0; i < claims.size(); i++) {
                ClaimResult claim = claims.get(i);
                if (i > 0) text.append("§7, ");
                text.append(clickableClaim(claim.claim()));
                text.append("§7 (1/" + claim.visibleCount() + (claim.openEnded() ? "+" : "") + ")");
            }
            return text;
        }

        private MutableText clickableClaim(String claim) {
            return Text.literal("§6" + claim).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/la info " + claim))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.translatable("dmls.chat.check_lands.hover_info", claim))));
        }

        private String ordinal(int number) {
            int lastTwo = number % 100;
            if (lastTwo >= 11 && lastTwo <= 13) return number + "th";
            return switch (number % 10) {
                case 1 -> number + "st";
                case 2 -> number + "nd";
                case 3 -> number + "rd";
                default -> number + "th";
            };
        }
    }
}
