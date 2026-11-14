package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.CheckLandsAltScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.AltsOutputParser;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Runs /alts for one IGN, then checks which lands the account and every linked alt belong to
 * (and with what rank), and prints one grouped report.
 */
public final class CheckLandsAltModule extends DMLSModule {
    public static final String OPERATION_ID = "check-lands-alts";
    private static final String PREFIX = "§8[§6DMLS - LandsAlts§8] §7";
    private static final int LAND_LIST_SLOT = ScreenUtils.slotIndex(6, 3);
    private static final int PLAYER_LIST_SLOT = ScreenUtils.slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;
    private static final int ALTS_TIMEOUT_TICKS = 20 * 10;
    private static final int ALTS_OUTPUT_QUIET_TICKS = 10;
    private static final int MAX_ACCOUNTS = 10;

    private final OperationCoordinator coordinator;
    private CheckSession activeSession;

    public CheckLandsAltModule() {
        this(OperationCoordinator.global());
    }

    CheckLandsAltModule(OperationCoordinator coordinator) {
        super(StaffRank.MODERATOR);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.check_lands_alts.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SOUL_CAMPFIRE);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.module.check_lands_alts.description.1"),
                Component.translatable("dmls.module.check_lands_alts.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new CheckLandsAltScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts one coordinator-owned /alts then lands-per-account sequence. */
    public OperationStartResult submit(Minecraft client, String ignInput) {
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;
        String ign = Objects.requireNonNullElse(ignInput, "").trim();
        if (!InputValidators.isUsername(ign)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return OperationStartResult.INVALID;
        }

        replaceOwnOperation(client);
        CheckSession candidate = new CheckSession(ign);
        activeSession = candidate;
        OperationStartResult result = coordinator.start(client, OPERATION_ID, "Check Lands (Alts)", candidate);
        if (result == OperationStartResult.STARTED && !candidate.acceptedAtStart()) {
            result = OperationStartResult.SERVER_BLOCKED;
        }
        if (result != OperationStartResult.STARTED && activeSession == candidate) activeSession = null;
        reportStartFailure(client, result);
        return result;
    }

    private void replaceOwnOperation(Minecraft client) {
        if (coordinator.activeDescriptor().filter(descriptor -> descriptor.operationId().equals(OPERATION_ID)).isPresent()) {
            coordinator.cancel(OPERATION_ID, client);
        }
    }

    private void reportStartFailure(Minecraft client, OperationStartResult result) {
        if (result == OperationStartResult.BUSY) {
            String owner = coordinator.activeDescriptor().map(descriptor -> descriptor.displayName())
                    .orElse("Another operation");
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
        } else if (result != OperationStartResult.STARTED && result != OperationStartResult.SERVER_BLOCKED
                && result != OperationStartResult.INVALID) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.start_failed");
        }
    }

    // ---------------------------------------------------------------- result types

    private record ClaimResult(String claim, int visibleCount, boolean openEnded) {
    }

    private static ClaimResult toClaimResult(String claim, Optional<RankStats> stats) {
        return stats.map(value -> new ClaimResult(claim, Math.max(1, value.visibleCount()), value.openEnded()))
                .orElseGet(() -> new ClaimResult(claim, 1, false));
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

    /** Everything we learned about the lands of one account. */
    private static final class AccountLands {
        private final String name;
        private final List<String> owned = new ArrayList<>();
        private final List<ClaimResult> admin = new ArrayList<>();
        private final List<RankClaims> custom = new ArrayList<>();
        private final List<String> memberOrUnknown = new ArrayList<>();

        private AccountLands(String name) {
            this.name = name;
        }

        private boolean isEmpty() {
            return owned.isEmpty() && admin.isEmpty() && custom.isEmpty() && memberOrUnknown.isEmpty();
        }

        private void addCustomRankClaim(RankAssignment rank, Optional<RankStats> stats, String claim) {
            for (RankClaims existing : custom) {
                if (existing.rank.equalsIgnoreCase(rank.customRank()) && existing.position == rank.position()) {
                    existing.addClaim(claim, stats);
                    return;
                }
            }
            RankClaims created = new RankClaims(rank.customRank(), rank.formattedCustomRank(), rank.position());
            created.addClaim(claim, stats);
            custom.add(created);
        }
    }

    private enum Stage {
        WAITING_TO_SEND_ALTS,
        WAITING_FOR_ALTS,
        WAITING_TO_SEND_LANDS,
        WAITING_FOR_LANDS,
        SENDING_NEXT_INFO_COMMAND,
        WAITING_FOR_INFO
    }

    // ---------------------------------------------------------------- session

    private final class CheckSession implements ManagedOperation {
        private final String ign;
        private final AltsOutputParser altsParser;
        private final Queue<String> remainingAccounts = new ArrayDeque<>();
        private final Queue<String> remainingClaims = new ArrayDeque<>();
        private final List<AccountLands> results = new ArrayList<>();
        private final List<String> skippedAccounts = new ArrayList<>();

        private OperationHandle handle;
        private Stage stage = Stage.WAITING_TO_SEND_ALTS;
        private int waitTicks;
        private int altsListQuietTicks;
        private boolean altsListStarted;
        private boolean altsResolved;
        private AccountLands current;
        private String currentClaim;
        private MenuCommandQuery activeQuery;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;
        private boolean initialDispatchFailed;

        private CheckSession(String ign) {
            this.ign = ign;
            this.altsParser = new AltsOutputParser(ign);
        }

        private boolean acceptedAtStart() {
            return !initialDispatchFailed;
        }

        @Override
        public void onStarted(OperationHandle handle, Minecraft client) {
            this.handle = handle;
            if (!handle.descriptor().dryRunCaptured()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.checking", ign);
            }
            trySendAlts(client);
        }

        @Override
        public void onTick(OperationHandle handle, Minecraft client) {
            switch (stage) {
                case WAITING_TO_SEND_ALTS -> trySendAlts(client);
                case WAITING_FOR_ALTS -> {
                    waitTicks++;
                    if (altsListStarted && ++altsListQuietTicks > ALTS_OUTPUT_QUIET_TICKS) {
                        finishAltsList(client);
                    } else if (!altsListStarted && waitTicks > ALTS_TIMEOUT_TICKS) {
                        failAltsReadAndContinue(client);
                    }
                }
                case WAITING_TO_SEND_LANDS -> trySendLandListCommand(client);
                case WAITING_FOR_LANDS -> waitForLands(client);
                case SENDING_NEXT_INFO_COMMAND -> sendNextInfoCommand(client);
                case WAITING_FOR_INFO -> waitForInfo(client);
            }
        }

        @Override
        public void onServerMessage(OperationHandle handle, Minecraft client, ServerMessage message) {
            if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            switch (stage) {
                case WAITING_FOR_ALTS -> handleAltsMessage(client, message.cleanText());
                case WAITING_FOR_LANDS, WAITING_FOR_INFO -> handleLandsMessage(client, message.cleanText());
                default -> { }
            }
        }

        @Override
        public void onCancelled(OperationHandle handle, Minecraft client, OperationCancelReason reason) {
            ScreenUtils.closeHandledScreen(client);
            remainingAccounts.clear();
            remainingClaims.clear();
            if (activeSession == this) activeSession = null;
            if (reason != OperationCancelReason.MODULE_REQUESTED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
                if (hasAnyResults()) report(client);
            }
        }

        // ------------------------------------------------------------ alts phase

        private void trySendAlts(Minecraft client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            initialDispatch = handle.dispatchCommand(client, "alts " + ign);
            initialDispatchFailed = initialDispatch == CommandDispatch.BLOCKED;
            if (initialDispatch == CommandDispatch.SENT) {
                waitTicks = 0;
                stage = Stage.WAITING_FOR_ALTS;
            } else if (initialDispatch == CommandDispatch.SIMULATED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.simulated", "/alts " + ign);
                finishAll(client, false);
            } else {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
            }
        }

        private void handleAltsMessage(Minecraft client, String message) {
            if (altsResolved) return;
            AltsOutputParser.Event event = altsParser.accept(message);
            switch (event) {
                case UNRELATED -> { }
                case LIST_STARTED -> {
                    altsListStarted = true;
                    altsListQuietTicks = 0;
                    waitTicks = 0;
                }
                case ACCOUNT_LINE -> {
                    altsListStarted = true;
                    altsListQuietTicks = 0;
                }
                case INLINE_RESULT -> resolveAlts(client, altsParser.alts());
                case NO_ALTS -> resolveNoAlts(client);
                case MALFORMED -> failAltsReadAndContinue(client);
            }
        }

        private void finishAltsList(Minecraft client) {
            AltsOutputParser.ListResult parsed = altsParser.finishList();
            switch (parsed.status()) {
                case FOUND -> resolveAlts(client, parsed.alts());
                case NO_ALTS -> resolveNoAlts(client);
                case NO_RESPONSE -> failAltsReadAndContinue(client);
            }
        }

        private void resolveAlts(Minecraft client, List<String> foundAlts) {
            if (foundAlts.isEmpty()) {
                resolveNoAlts(client);
                return;
            }
            List<String> alts = new ArrayList<>(foundAlts);
            if (alts.size() > MAX_ACCOUNTS - 1) {
                skippedAccounts.addAll(alts.subList(MAX_ACCOUNTS - 1, alts.size()));
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.found_limited",
                        alts.size(), MAX_ACCOUNTS - 1);
                alts = new ArrayList<>(alts.subList(0, MAX_ACCOUNTS - 1));
            } else {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        alts.size() == 1 ? "dmls.chat.check_alts.found.one" : "dmls.chat.check_alts.found.many",
                        alts.size(), String.join(", ", alts));
            }
            beginLandChecks(client, alts);
        }

        private void resolveNoAlts(Minecraft client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.no_alts", ign);
            beginLandChecks(client, List.of());
        }

        private void failAltsReadAndContinue(Minecraft client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.read_failed", ign);
            beginLandChecks(client, List.of());
        }

        private void beginLandChecks(Minecraft client, List<String> alts) {
            if (altsResolved) return;
            altsResolved = true;
            remainingAccounts.add(ign);
            remainingAccounts.addAll(alts);
            startNextAccount(client);
        }

        // ------------------------------------------------------------ lands phase

        private void startNextAccount(Minecraft client) {
            String account = remainingAccounts.poll();
            if (account == null) {
                finishAll(client, true);
                return;
            }
            current = new AccountLands(account);
            results.add(current);
            remainingClaims.clear();
            currentClaim = null;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands.checking", account);
            activeQuery = new MenuCommandQuery("lands info player " + account, "Player " + account,
                    MENU_TIMEOUT_TICKS, LAND_LIST_SLOT);
            stage = Stage.WAITING_TO_SEND_LANDS;
            trySendLandListCommand(client);
        }

        private void trySendLandListCommand(Minecraft client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            CommandDispatch dispatch = activeQuery.start(client, handle::dispatchCommand);
            if (dispatch == CommandDispatch.BLOCKED) {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                return;
            }
            stage = Stage.WAITING_FOR_LANDS;
        }

        private void handleLandsMessage(Minecraft client, String message) {
            if (current == null) return;
            String lower = message.toLowerCase(Locale.ROOT);

            // Account has no lands profile: skip to the next account.
            if (stage == Stage.WAITING_FOR_LANDS
                    && lower.contains("no player with the name")
                    && lower.contains(current.name.toLowerCase(Locale.ROOT))) {
                finishAccount(client);
                return;
            }

            // A listed claim no longer exists: skip only that claim.
            if (stage == Stage.WAITING_FOR_INFO
                    && currentClaim != null
                    && lower.contains("doesn't exist")
                    && lower.contains(currentClaim.toLowerCase(Locale.ROOT))) {
                ScreenUtils.closeHandledScreen(client);
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands_alts.skipped_claim", currentClaim);
                activeQuery = null;
                stage = Stage.SENDING_NEXT_INFO_COMMAND;
            }
        }

        private void waitForLands(Minecraft client) {
            MenuCommandQuery.TickResult tick = activeQuery.tick(client);
            if (!handleTerminalQueryStatus(client, tick, "/lands info player " + current.name)) return;
            if (tick.status() != MenuCommandQuery.Status.READY) return;

            LandList parsed = tick.result().flatMap(result -> result.tooltip(LAND_LIST_SLOT))
                    .map(LandsMenuParser::parseLands).orElseGet(() -> LandsMenuParser.parseLands(List.of()));
            if (parsed.status() == LandsMenuParser.ParseStatus.MALFORMED) {
                malformed(client, "/lands info player " + current.name);
                return;
            }
            if (parsed.lands().isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_lands.none", current.name);
                finishAccount(client);
                return;
            }
            remainingClaims.addAll(parsed.lands());
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    parsed.lands().size() == 1 ? "dmls.chat.check_lands.found.one" : "dmls.chat.check_lands.found.many",
                    parsed.lands().size(), current.name);
            stage = Stage.SENDING_NEXT_INFO_COMMAND;
        }

        private void sendNextInfoCommand(Minecraft client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            currentClaim = remainingClaims.poll();
            if (currentClaim == null) {
                finishAccount(client);
                return;
            }
            activeQuery = new MenuCommandQuery("lands info land " + currentClaim, currentClaim,
                    MENU_TIMEOUT_TICKS, PLAYER_LIST_SLOT);
            CommandDispatch dispatch = activeQuery.start(client, handle::dispatchCommand);
            if (dispatch == CommandDispatch.BLOCKED) {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                return;
            }
            stage = Stage.WAITING_FOR_INFO;
        }

        private void waitForInfo(Minecraft client) {
            if (activeQuery == null) return;
            MenuCommandQuery.TickResult tick = activeQuery.tick(client);
            if (!handleTerminalQueryStatus(client, tick, "/lands info land " + currentClaim)) return;
            if (tick.status() != MenuCommandQuery.Status.READY) return;

            String account = current.name;
            RankScan scan = tick.result().flatMap(result -> result.tooltip(PLAYER_LIST_SLOT))
                    .map(tooltip -> LandsMenuParser.parsePlayerRank(tooltip, account))
                    .orElseGet(() -> LandsMenuParser.parsePlayerRank(List.of(), account));
            if (scan.status() == LandsMenuParser.ParseStatus.MALFORMED) {
                malformed(client, "/lands info land " + currentClaim);
                return;
            }
            RankAssignment rank = scan.assignment();
            switch (rank.type()) {
                case OWNER -> current.owned.add(currentClaim);
                case ADMIN -> current.admin.add(toClaimResult(currentClaim,
                        findRankStats(scan.stats(), "Admin", Integer.MAX_VALUE)));
                case CUSTOM -> current.addCustomRankClaim(rank,
                        findRankStats(scan.stats(), rank.customRank(), rank.position()), currentClaim);
                case MEMBER_OR_UNKNOWN -> current.memberOrUnknown.add(currentClaim);
            }
            stage = Stage.SENDING_NEXT_INFO_COMMAND;
        }

        /** Returns true only when the caller may continue inspecting this tick result. */
        private boolean handleTerminalQueryStatus(Minecraft client, MenuCommandQuery.TickResult tick, String command) {
            switch (tick.status()) {
                case WAITING -> { return false; }
                case TIMED_OUT -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.timeout", command);
                    finishAll(client, true);
                    return false;
                }
                case CANCELLED -> {
                    handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                    return false;
                }
                case SIMULATED -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.simulated", command);
                    finishAccount(client);
                    return false;
                }
                case READY -> { return true; }
            }
            return false;
        }

        private void malformed(Minecraft client, String command) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.malformed", command);
            finishAll(client, true);
        }

        private void finishAccount(Minecraft client) {
            ScreenUtils.closeHandledScreen(client);
            activeQuery = null;
            startNextAccount(client);
        }

        private void finishAll(Minecraft client, boolean showReport) {
            ScreenUtils.closeHandledScreen(client);
            remainingAccounts.clear();
            remainingClaims.clear();
            if (activeSession == this) activeSession = null;
            if (showReport && hasAnyResults()) report(client);
            if (handle != null) handle.complete();
        }

        private boolean hasAnyResults() {
            return !results.isEmpty();
        }

        private Optional<RankStats> findRankStats(List<RankStats> stats, String rank, int position) {
            return stats.stream().filter(value -> value.rank().equalsIgnoreCase(rank)
                    && (position == Integer.MAX_VALUE || value.position() == position)).findFirst();
        }

        // ------------------------------------------------------------ report

        private void report(Minecraft client) {
            String header = PREFIX + Component.translatable("dmls.chat.check_lands_alts.header", ign).getString() + " ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (AccountLands account : results) {
                MutableComponent title = Component.literal("§8▸ §6" + account.name
                                + (account.name.equalsIgnoreCase(ign) ? " §8(" : " §8("))
                        .append(Component.translatable(account.name.equalsIgnoreCase(ign)
                                ? "dmls.chat.check_lands_alts.main" : "dmls.chat.check_lands_alts.alt"))
                        .append("§8)§7:");
                ChatUtils.sendClientMessage(client, title);
                if (account.isEmpty()) {
                    ChatUtils.sendClientMessage(client, Component.literal("   ")
                            .append(Component.translatable("dmls.chat.check_lands_alts.no_lands")));
                    continue;
                }
                if (!account.owned.isEmpty()) {
                    ChatUtils.sendClientMessage(client, Component.literal("   ")
                            .append(Component.translatable("dmls.chat.check_lands.owner"))
                            .append(claimsText(account.owned)));
                }
                if (!account.admin.isEmpty()) {
                    ChatUtils.sendClientMessage(client, Component.literal("   ")
                            .append(Component.translatable("dmls.chat.check_lands.admin"))
                            .append(claimResultsText(account.admin)));
                }
                account.custom.stream()
                        .sorted(Comparator.comparingInt((RankClaims rank) -> rank.position)
                                .thenComparing(rank -> rank.rank, String.CASE_INSENSITIVE_ORDER))
                        .forEach(rank -> ChatUtils.sendClientMessage(client,
                                Component.literal("   " + rank.formattedRank + "§r§7 (" + ordinal(rank.position)
                                        + " rank): ").append(claimResultsText(rank.claims))));
                if (!account.memberOrUnknown.isEmpty()) {
                    ChatUtils.sendClientMessage(client, Component.literal("   ")
                            .append(Component.translatable("dmls.chat.check_lands.member_unknown"))
                            .append(claimsText(account.memberOrUnknown)));
                }
            }
            if (!skippedAccounts.isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.check_alts.skipped",
                        String.join(", ", skippedAccounts));
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private MutableComponent claimsText(List<String> claims) {
            MutableComponent text = Component.literal("");
            for (int i = 0; i < claims.size(); i++) {
                if (i > 0) text.append("§7, ");
                text.append(clickableClaim(claims.get(i)));
            }
            return text;
        }

        private MutableComponent claimResultsText(List<ClaimResult> claims) {
            MutableComponent text = Component.literal("");
            for (int i = 0; i < claims.size(); i++) {
                ClaimResult claim = claims.get(i);
                if (i > 0) text.append("§7, ");
                text.append(clickableClaim(claim.claim()));
                text.append("§7 (1/" + claim.visibleCount() + (claim.openEnded() ? "+" : "") + ")");
            }
            return text;
        }

        private MutableComponent clickableClaim(String claim) {
            return Component.literal("§6" + claim).withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/lands info land " + claim))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.translatable("dmls.chat.check_lands.hover_info", claim))));
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