package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.XrayRollbackScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.parser.CoreProtectResponseParser;
import com.duperknight.client.parser.BalanceResponseParser;
import com.duperknight.client.session.PendingConfirmation;
import com.duperknight.client.session.RollbackSafetyGate;
import com.duperknight.client.session.OperationOutcome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class XrayRollbackModule extends DMLSModule {
    public static final String OPERATION_ID = "xray-rollback";
    private static final String PREFIX = "§8[§6DMLS - Xray§8] §7";
    private static final int ROLLBACK_TIMEOUT_TICKS = 20 * 60;
    private static final int BALANCE_WAIT_TICKS = 20 * 2;
    private static final int COMMAND_GAP_TICKS = 20 * 2;
    private static final List<Step> STEPS = List.of(
            new Step("dmls.chat.xray.step.deepslate",
                    "co rollback u:%s t:30d r:#global a:block i:deepslate,deepslate_gold_ore,deepslate_emerald_ore,deepslate_diamond_ore,deepslate_iron_ore,deepslate_lapis_ore,deepslate_redstone_ore,deepslate_copper_ore,tuff",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.stone",
                    "co rollback u:%s t:30d r:#global a:block i:stone,gold_ore,iron_ore,emerald_ore,diamond_ore,redstone_ore,lapis_ore,coal_ore,granite,diorite,andesite,gravel",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.containers",
                    "co rollback u:%s t:7d r:#global a:container",
                    StepType.ROLLBACK),
            new Step("dmls.chat.xray.step.balance",
                    "bal %s",
                    StepType.BALANCE)
    );

    private PendingConfirmation<RollbackRequest> pendingConfirmation;

    public XrayRollbackModule() {
        super(StaffRank.SENIOR_MODERATOR);
    }

    public enum PreparationStatus { VALID, INVALID_USERNAME }

    public enum StageStatus { STAGED, INVALID, BLOCKED, BUSY }

    public record StageResult(StageStatus status, String token, RollbackRequest request) {
        public boolean staged() { return status == StageStatus.STAGED; }
    }

    /** Frozen destructive request shared by preview and execution. */
    public record RollbackRequest(PreparationStatus status, String username, List<String> commands) {
        public boolean valid() {
            return status == PreparationStatus.VALID;
        }
    }

    public static RollbackRequest prepare(String username) {
        String cleanUsername = username == null ? "" : username.trim();
        if (!InputValidators.isUsername(cleanUsername)) {
            return new RollbackRequest(PreparationStatus.INVALID_USERNAME, cleanUsername, List.of());
        }
        return new RollbackRequest(PreparationStatus.VALID, cleanUsername,
                STEPS.stream().map(step -> step.commandTemplate().formatted(cleanUsername)).toList());
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.xray.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.DEEPSLATE_DIAMOND_ORE);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.xray.description.1"),
                Text.translatable("dmls.module.xray.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new XrayRollbackScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Compatibility entrypoint: prepares the rollback but never dispatches before confirmation. */
    public StageResult submit(MinecraftClient client, String ign) {
        return stage(client, ign);
    }

    public StageResult stage(MinecraftClient client, String ign) {
        return stage(client, ign, true);
    }

    public StageResult stage(MinecraftClient client, String ign, boolean announcePreview) {
        invalidatePending();
        RollbackRequest request = prepare(ign);
        if (!request.valid()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return new StageResult(StageStatus.INVALID, "", request);
        }
        if (!canRunPrivilegedOperation(client)) {
            return new StageResult(StageStatus.BLOCKED, "", request);
        }

        if (OperationCoordinator.global().isBusy()) {
            String owner = OperationCoordinator.global().activeDescriptor()
                    .map(descriptor -> descriptor.displayName()).orElse("another operation");
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
            return new StageResult(StageStatus.BUSY, "", request);
        }

        pendingConfirmation = new PendingConfirmation<>(request);
        if (announcePreview) sendPreview(client, pendingConfirmation);
        return new StageResult(StageStatus.STAGED, pendingConfirmation.token(), request);
    }

    public boolean confirm(MinecraftClient client, String token) {
        PendingConfirmation<RollbackRequest> pending = pendingConfirmation;
        if (pending == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.none");
            return false;
        }
        PendingConfirmation.ConsumeResult<RollbackRequest> consumed = pending.consume(token);
        if (consumed.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, confirmationError(consumed.status()));
            return false;
        }
        pendingConfirmation = null;
        if (!canRunPrivilegedOperation(client)) return false;

        RollbackSession operation = new RollbackSession(consumed.request().orElseThrow().username());
        var started = OperationCoordinator.global().start(client, OPERATION_ID,
                displayName().getString(), operation);
        if (started != com.duperknight.client.session.OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.not_started", started.name());
            return false;
        }
        return operation.acceptedAtStart();
    }

    /** Confirms the single currently staged command request without exposing its internal nonce. */
    public boolean confirm(MinecraftClient client) {
        PendingConfirmation<RollbackRequest> pending = pendingConfirmation;
        return confirm(client, pending == null ? "" : pending.token());
    }

    public boolean isPending(String token) {
        PendingConfirmation<RollbackRequest> pending = pendingConfirmation;
        return pending != null && pending.token().equals(token) && pending.isActive();
    }

    public void invalidatePending(String token) {
        PendingConfirmation<RollbackRequest> pending = pendingConfirmation;
        if (pending != null && pending.token().equals(token)) invalidatePending();
    }

    public boolean cancel(MinecraftClient client) {
        if (pendingConfirmation != null && pendingConfirmation.isActive()) {
            invalidatePending();
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.cancelled");
            return true;
        }
        boolean cancelled = OperationCoordinator.global().cancel(OPERATION_ID, client)
                == com.duperknight.client.session.OperationCancelResult.CANCELLED;
        if (!cancelled) ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.none");
        return cancelled;
    }

    private void invalidatePending() {
        if (pendingConfirmation != null) pendingConfirmation.invalidate();
        pendingConfirmation = null;
    }

    private void sendPreview(MinecraftClient client, PendingConfirmation<RollbackRequest> pending) {
        RollbackRequest request = pending.request();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.preview", request.username());
        request.commands().forEach(command -> ChatUtils.sendClientMessage(client, "§8• §7/" + command));
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.instructions",
                "/dmls xray confirm", "/dmls xray cancel");
    }

    private static String confirmationError(PendingConfirmation.ConsumeStatus status) {
        return switch (status) {
            case EXPIRED -> "dmls.chat.confirmation.expired";
            case INVALID_TOKEN -> "dmls.chat.confirmation.invalid";
            case ALREADY_CONSUMED, INVALIDATED -> "dmls.chat.confirmation.none";
            case CONFIRMED -> throw new IllegalArgumentException("confirmed is not an error");
        };
    }

    public List<Text> previewLines(RollbackRequest request) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("dmls.screen.xray.review_target", request.username()));
        request.commands().forEach(command -> lines.add(Text.literal("/" + command)));
        return List.copyOf(lines);
    }

    private record Step(String label, String commandTemplate, StepType type) {
    }

    private enum StepType { ROLLBACK, BALANCE }

    private final class RollbackSession implements ManagedOperation {
        private final String ign;
        private final List<Text> results = new ArrayList<>();

        private OperationHandle handle;
        private int stepIndex = -1;
        private int waitTicks;
        private int postCompletionTicks;
        private RollbackSafetyGate safetyGate;
        private OperationOutcome balanceOutcome;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;

        private RollbackSession(String ign) {
            this.ign = ign;
        }

        private boolean acceptedAtStart() {
            return initialDispatch != CommandDispatch.BLOCKED;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            this.handle = handle;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.start", ign, STEPS.size());
            nextStep(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            tick(client);
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
            if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            handleServerMessage(message.cleanText());
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            if (safetyGate != null) safetyGate.cancel();
            if (stepIndex >= 0 && stepIndex < STEPS.size()) {
                results.add(Text.translatable("dmls.chat.xray.result.cancelled", Text.translatable(STEPS.get(stepIndex).label())));
            }
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    reason == OperationCancelReason.CONNECTION_CHANGED
                            ? "dmls.chat.xray.cancelled.connection" : "dmls.chat.xray.cancelled.user");
            report(client);
        }

        private void tick(MinecraftClient client) {
            if (stepIndex >= STEPS.size()) {
                return;
            }

            Step step = STEPS.get(stepIndex);
            if (step.type() == StepType.ROLLBACK) {
                OperationOutcome outcome = safetyGate.tick();
                if (outcome == OperationOutcome.CONFIRMED) {
                    // small gap after the completion message before the next command
                    if (postCompletionTicks++ >= COMMAND_GAP_TICKS) {
                        results.add(Text.translatable("dmls.chat.xray.result.success", Text.translatable(step.label())));
                        nextStep(client);
                    }
                } else if (outcome == OperationOutcome.TIMED_OUT) {
                    results.add(Text.translatable("dmls.chat.xray.result.timed_out", Text.translatable(step.label())));
                    report(client);
                    finish();
                }
            } else if (balanceOutcome == OperationOutcome.CONFIRMED) {
                results.add(Text.translatable("dmls.chat.xray.result.balance_confirmed", Text.translatable(step.label())));
                nextStep(client);
            } else if (balanceOutcome == OperationOutcome.REJECTED) {
                results.add(Text.translatable("dmls.chat.xray.result.balance_rejected", Text.translatable(step.label())));
                nextStep(client);
            } else if (++waitTicks > BALANCE_WAIT_TICKS) {
                results.add(Text.translatable("dmls.chat.xray.result.output", Text.translatable(step.label())));
                nextStep(client);
            }
        }

        private void handleServerMessage(String message) {
            if (stepIndex < 0 || stepIndex >= STEPS.size()) {
                return;
            }

            if (STEPS.get(stepIndex).type() == StepType.BALANCE) {
                switch (BalanceResponseParser.parse(ign, message)) {
                    case CONFIRMED -> balanceOutcome = OperationOutcome.CONFIRMED;
                    case REJECTED -> balanceOutcome = OperationOutcome.REJECTED;
                    case UNRELATED -> { }
                }
                return;
            }

            switch (CoreProtectResponseParser.parse(message)) {
                case CONFIRMED -> safetyGate.confirm();
                case REJECTED -> {
                    safetyGate.reject();
                    results.add(Text.translatable("dmls.chat.xray.result.rejected", Text.translatable(STEPS.get(stepIndex).label())));
                    MinecraftClient client = MinecraftClient.getInstance();
                    report(client);
                    finish();
                }
                case UNRELATED -> { }
            }
        }

        private void nextStep(MinecraftClient client) {
            stepIndex++;
            if (stepIndex >= STEPS.size()) {
                report(client);
                finish();
                return;
            }

            Step step = STEPS.get(stepIndex);
            waitTicks = 0;
            postCompletionTicks = 0;
            safetyGate = step.type() == StepType.ROLLBACK ? new RollbackSafetyGate(ROLLBACK_TIMEOUT_TICKS) : null;
            balanceOutcome = step.type() == StepType.BALANCE ? OperationOutcome.PENDING : null;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.xray.step", stepIndex + 1, STEPS.size(),
                    Text.translatable(step.label()));
            CommandDispatch dispatch = handle.dispatchCommand(client, step.commandTemplate().formatted(ign));
            if (stepIndex == 0) initialDispatch = dispatch;
            if (dispatch == CommandDispatch.BLOCKED) {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                return;
            }

            if (dispatch == CommandDispatch.SIMULATED) {
                if (safetyGate != null) safetyGate.confirm();
                else balanceOutcome = OperationOutcome.CONFIRMED;
            }
        }

        private void report(MinecraftClient client) {
            String header = Text.translatable("dmls.chat.xray.header", ign).getString();
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (Text result : results) {
                ChatUtils.sendClientMessage(client, result);
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void finish() {
            if (handle != null) handle.complete();
        }
    }
}
