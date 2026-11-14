package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.CheckAltsScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.AltsOutputParser;
import com.duperknight.client.parser.HistoryOutputParser;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CheckAltsModule extends DMLSModule {
    public static final String OPERATION_ID = "check-alts";
    private static final String PREFIX = "§8[§6DMLS - CheckAlts§8] §7";
    private static final int ALTS_TIMEOUT_TICKS = 20 * 10;
    private static final int ALTS_OUTPUT_QUIET_TICKS = 10;
    private static final int HISTORY_WINDOW_TICKS = 20 * 3;
    private static final int MAX_ACCOUNTS = 10;

    private final OperationCoordinator coordinator;
    private CheckSession activeSession;

    public CheckAltsModule() {
        this(OperationCoordinator.global());
    }

    CheckAltsModule(OperationCoordinator coordinator) {
        super(StaffRank.MODERATOR);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.check_alts.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.module.check_alts.description.1"),
                Component.translatable("dmls.module.check_alts.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new CheckAltsScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts one coordinator-owned /alts and /history sequence. */
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
        OperationStartResult result = coordinator.start(client, OPERATION_ID, "Check Alts", candidate);
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

    private enum Stage {
        WAITING_TO_SEND_ALTS,
        WAITING_FOR_ALTS,
        WAITING_TO_SEND_HISTORY,
        WAITING_FOR_HISTORY
    }

    private record PunishmentStats(String name, HistoryOutputParser.Result result) {
    }

    private final class CheckSession implements ManagedOperation {
        private final String ign;
        private final AltsOutputParser altsParser;
        private final Queue<String> remainingAccounts = new ArrayDeque<>();
        private final List<PunishmentStats> results = new ArrayList<>();
        private final List<String> skippedAccounts = new ArrayList<>();

        private OperationHandle handle;
        private Stage stage = Stage.WAITING_TO_SEND_ALTS;
        private int waitTicks;
        private int altsListQuietTicks;
        private boolean altsListStarted;
        private boolean altsResolved;
        private String currentAccount;
        private HistoryOutputParser historyParser;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;
        private boolean initialDispatchFailed;

        private CheckSession(String ign) {
            this.ign = ign;
            this.altsParser = new AltsOutputParser(ign);
        }

        @Override
        public void onStarted(OperationHandle handle, Minecraft client) {
            this.handle = handle;
            if (!handle.descriptor().dryRunCaptured()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.checking", ign);
            }
            trySendAlts(client);
        }

        private boolean acceptedAtStart() {
            return !initialDispatchFailed;
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
                case WAITING_TO_SEND_HISTORY -> trySendNextHistoryCommand(client);
                case WAITING_FOR_HISTORY -> {
                    waitTicks++;
                    if (waitTicks > HISTORY_WINDOW_TICKS) {
                        finishCurrentHistory();
                        stage = Stage.WAITING_TO_SEND_HISTORY;
                    }
                }
            }
        }

        @Override
        public void onServerMessage(OperationHandle handle, Minecraft client, ServerMessage message) {
            if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            switch (stage) {
                case WAITING_TO_SEND_ALTS, WAITING_TO_SEND_HISTORY -> { }
                case WAITING_FOR_ALTS -> handleAltsMessage(client, message.cleanText());
                case WAITING_FOR_HISTORY -> handleHistoryMessage(client, message.cleanText());
            }
        }

        @Override
        public void onCancelled(OperationHandle handle, Minecraft client, OperationCancelReason reason) {
            remainingAccounts.clear();
            historyParser = null;
            currentAccount = null;
            if (activeSession == this) activeSession = null;
            if (reason != OperationCancelReason.MODULE_REQUESTED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
                if (!results.isEmpty()) report(client);
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
            beginHistoryChecks(client, alts);
        }

        private void resolveNoAlts(Minecraft client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.no_alts", ign);
            beginHistoryChecks(client, List.of());
        }

        private void failAltsReadAndContinue(Minecraft client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.read_failed", ign);
            beginHistoryChecks(client, List.of());
        }

        private void beginHistoryChecks(Minecraft client, List<String> alts) {
            if (altsResolved) return;
            altsResolved = true;
            remainingAccounts.add(ign);
            remainingAccounts.addAll(alts);
            stage = Stage.WAITING_TO_SEND_HISTORY;
        }

        private void handleHistoryMessage(Minecraft client, String message) {
            if (historyParser == null) return;
            HistoryOutputParser.Event event = historyParser.accept(message);
            if (event == HistoryOutputParser.Event.RECOGNIZED) waitTicks = 0;
            if (event == HistoryOutputParser.Event.COMPLETE || event == HistoryOutputParser.Event.FAILED) {
                finishCurrentHistory();
                stage = Stage.WAITING_TO_SEND_HISTORY;
            }
        }

        private void finishCurrentHistory() {
            if (historyParser == null || currentAccount == null) return;
            results.add(new PunishmentStats(currentAccount, historyParser.result()));
            historyParser = null;
            currentAccount = null;
        }

        private void trySendAlts(Minecraft client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            initialDispatch = handle.dispatchCommand(client, "alts " + ign);
            initialDispatchFailed = initialDispatch == CommandDispatch.BLOCKED;
            if (initialDispatch == CommandDispatch.SENT) {
                waitTicks = 0;
                stage = Stage.WAITING_FOR_ALTS;
            }
            handleDispatch(client, initialDispatch, "/alts " + ign);
        }

        private void trySendNextHistoryCommand(Minecraft client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            String next = remainingAccounts.poll();
            if (next == null) {
                finish(client);
                report(client);
                return;
            }

            currentAccount = next;
            historyParser = new HistoryOutputParser(next);
            waitTicks = 0;
            stage = Stage.WAITING_FOR_HISTORY;
            handleDispatch(client, handle.dispatchCommand(client, "history " + next), "/history " + next);
        }

        private void handleDispatch(Minecraft client, CommandDispatch dispatch, String shownCommand) {
            if (dispatch == CommandDispatch.SENT) return;
            if (dispatch == CommandDispatch.SIMULATED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.simulated", shownCommand);
                finish(client);
                return;
            }
            handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
        }

        private void report(Minecraft client) {
            String header = PREFIX + "Alt history of §6" + ign + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (PunishmentStats stats : results) {
                MutableComponent line = Component.literal("§8• ").append(clickableName(stats.name())).append("§7: ");
                HistoryOutputParser.Result result = stats.result();
                switch (result.status()) {
                    case CLEAN -> line.append("§aclean");
                    case NEVER_JOINED -> line.append(Component.translatable("dmls.chat.check_alts.never_joined"));
                    case UNKNOWN -> line.append(Component.translatable("dmls.chat.check_alts.unknown"));
                    case MALFORMED -> line.append(Component.translatable("dmls.chat.check_alts.parse_failed"));
                    case PARSED -> appendPunishmentCounts(line, result);
                }
                ChatUtils.sendClientMessage(client, line);
            }
            ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.check_alts.counts_note");
            if (!skippedAccounts.isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.check_alts.skipped",
                        String.join(", ", skippedAccounts));
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void appendPunishmentCounts(MutableComponent line, HistoryOutputParser.Result result) {
            List<String> parts = new ArrayList<>();
            if (result.bans() > 0) parts.add("§cBans: " + result.bans());
            if (result.mutes() > 0) parts.add("§6Mutes: " + result.mutes());
            if (result.warns() > 0) parts.add("§eWarns: " + result.warns());
            if (result.kicks() > 0) parts.add("§7Kicks: " + result.kicks());
            line.append(String.join("§7, ", parts));
        }

        private MutableComponent clickableName(String name) {
            return Component.literal("§6" + name).withStyle(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/history " + name))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.translatable("dmls.chat.check_alts.hover_history", name))));
        }

        private void finish(Minecraft client) {
            remainingAccounts.clear();
            historyParser = null;
            currentAccount = null;
            if (activeSession == this) activeSession = null;
            if (handle != null) handle.complete();
        }
    }
}
