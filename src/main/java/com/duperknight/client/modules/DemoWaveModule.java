package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.DemoWaveScreen;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.PendingConfirmation;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Removes a staff rank from a confirmed, paced batch. */
public final class DemoWaveModule extends DMLSModule {
    public static final String OPERATION_ID = "demo-wave";
    public static final int MAX_PLAYERS = 60;
    private static final String PREFIX = "§8[§6DMLS - DemoWave§8] §7";
    private static final List<String> RANKS = List.of("helper", "mod", "sr-mod", "support", "admin");

    private PendingConfirmation<DemotionRequest> pendingConfirmation;

    public DemoWaveModule() { super(StaffRank.ADMIN); }

    public static List<String> ranks() { return RANKS; }

    public record DemotionRequest(PreparationStatus status, String rank, List<String> usernames,
                                  List<String> skipped, List<String> commands) {
        public boolean valid() { return status == PreparationStatus.VALID; }
    }

    public enum PreparationStatus { VALID, UNKNOWN_RANK, EMPTY, TOO_MANY }
    public enum StageStatus { STAGED, INVALID, BLOCKED, BUSY }
    public record StageResult(StageStatus status, String token, DemotionRequest request) {
        public boolean staged() { return status == StageStatus.STAGED; }
    }

    public static DemotionRequest prepare(String rank, String input) {
        String cleanRank = rank == null ? "" : rank.trim().toLowerCase(Locale.ROOT);
        if (!RANKS.contains(cleanRank)) {
            return new DemotionRequest(PreparationStatus.UNKNOWN_RANK, cleanRank, List.of(), List.of(), List.of());
        }
        List<String> skipped = new ArrayList<>();
        List<String> usernames = InputValidators.uniqueUsernames(input, skipped);
        if (usernames.isEmpty()) {
            return new DemotionRequest(PreparationStatus.EMPTY, cleanRank, List.of(), List.copyOf(skipped), List.of());
        }
        if (usernames.size() > MAX_PLAYERS) {
            return new DemotionRequest(PreparationStatus.TOO_MANY, cleanRank, List.copyOf(usernames),
                    List.copyOf(skipped), List.of());
        }
        List<String> commands = usernames.stream()
                .map(username -> "lp user %s parent remove %s".formatted(username, cleanRank)).toList();
        return new DemotionRequest(PreparationStatus.VALID, cleanRank, List.copyOf(usernames),
                List.copyOf(skipped), commands);
    }

    @Override public Text displayName() { return Text.translatable("dmls.module.demo_wave.name"); }
    @Override public ItemStack icon() { return new ItemStack(Items.LEATHER_HELMET); }
    @Override public List<Text> description() {
        return List.of(Text.translatable("dmls.module.demo_wave.description.1"),
                Text.translatable("dmls.module.demo_wave.description.2"));
    }
    @Override public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new DemoWaveScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    public StageResult submit(MinecraftClient client, String rank, String input) {
        return stage(client, rank, input);
    }
    public StageResult stage(MinecraftClient client, String rank, String input) {
        return stage(client, rank, input, true);
    }

    public StageResult stage(MinecraftClient client, String rank, String input, boolean announcePreview) {
        invalidatePending();
        DemotionRequest request = prepare(rank, input);
        if (!request.skipped().isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    request.skipped().size() == 1 ? "dmls.chat.demo.skipping.one" : "dmls.chat.demo.skipping.many",
                    String.join(", ", request.skipped()));
        }
        if (!request.valid()) {
            reportPreparationError(client, request);
            return new StageResult(StageStatus.INVALID, "", request);
        }
        if (!canRunPrivilegedOperation(client)) return new StageResult(StageStatus.BLOCKED, "", request);
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
        PendingConfirmation<DemotionRequest> pending = pendingConfirmation;
        if (pending == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.none");
            return false;
        }
        var consumed = pending.consume(token);
        if (consumed.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, confirmationError(consumed.status()));
            return false;
        }
        pendingConfirmation = null;
        if (!canRunPrivilegedOperation(client)) return false;

        DemotionRequest request = consumed.request().orElseThrow();
        List<RankWaveOperation.Step> steps = request.usernames().stream()
                .map(username -> RankWaveOperation.step(username,
                        "lp user %s parent remove %s".formatted(username, request.rank()), true))
                .toList();
        RankWaveOperation operation = new RankWaveOperation(steps, request.usernames().size(), listener(request));
        OperationStartResult result = OperationCoordinator.global().start(client, OPERATION_ID,
                displayName().getString(), operation);
        if (result != OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.not_started", result.name());
            return false;
        }
        return operation.acceptedAtStart();
    }

    /** Confirms the single currently staged command request without exposing its internal nonce. */
    public boolean confirm(MinecraftClient client) {
        PendingConfirmation<DemotionRequest> pending = pendingConfirmation;
        return confirm(client, pending == null ? "" : pending.token());
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

    public boolean isPending(String token) {
        return pendingConfirmation != null && pendingConfirmation.token().equals(token)
                && pendingConfirmation.isActive();
    }
    public void invalidatePending(String token) {
        if (pendingConfirmation != null && pendingConfirmation.token().equals(token)) invalidatePending();
    }
    public List<Text> previewLines(DemotionRequest request) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("dmls.screen.demo.review_summary", request.usernames().size(), request.rank()));
        request.commands().forEach(command -> lines.add(Text.literal("/" + command)));
        return List.copyOf(lines);
    }

    private RankWaveOperation.Listener listener(DemotionRequest request) {
        return new RankWaveOperation.Listener() {
            @Override public void started(MinecraftClient client, int count) {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        count == 1 ? "dmls.chat.demo.start.one" : "dmls.chat.demo.start.many", count, request.rank());
            }
            @Override public void progress(MinecraftClient client, RankWaveOperation.Step step, int index, int total) {
                int playerIndex = request.usernames().indexOf(step.username()) + 1;
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.progress",
                        step.username(), playerIndex, request.usernames().size());
            }
            @Override public void finished(MinecraftClient client, RankWaveOperation.Summary summary) {
                if (summary.state() == PacedCommandSequence.State.COMPLETED) {
                    List<String> players = summary.simulatedPlayers().isEmpty()
                            ? summary.confirmedPlayers() : summary.simulatedPlayers();
                    String stem = summary.simulatedPlayers().isEmpty() ? "confirmed" : "simulated";
                    ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.demo." + stem + (players.size() == 1 ? ".one" : ".many"),
                            players.size(), request.rank(), String.join(", ", players));
                } else reportAbort(client, summary, request, null);
            }
            @Override public void cancelled(MinecraftClient client, RankWaveOperation.Summary summary,
                                             OperationCancelReason reason) {
                reportAbort(client, summary, request, reason);
            }
        };
    }

    private void reportAbort(MinecraftClient client, RankWaveOperation.Summary summary,
                             DemotionRequest request, OperationCancelReason reason) {
        int completed = summary.confirmedPlayers().size() + summary.simulatedPlayers().size();
        String suffix = switch (summary.state()) {
            case REJECTED -> "rejected";
            case TIMED_OUT -> "timed_out";
            case BLOCKED -> "blocked";
            case FAILED -> "failed";
            case CANCELLED -> reason == OperationCancelReason.CONNECTION_CHANGED
                    ? "cancelled_connection" : "cancelled";
            default -> "partial";
        };
        String interrupted = summary.interruptedStep() == null ? "-" : summary.interruptedStep().username();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo." + suffix,
                completed, request.usernames().size(), interrupted, request.rank());
    }

    private void reportPreparationError(MinecraftClient client, DemotionRequest request) {
        switch (request.status()) {
            case UNKNOWN_RANK -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                    "dmls.chat.demo.unknown_rank", request.rank(), String.join(", ", RANKS));
            case EMPTY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            case TOO_MANY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.too_many", MAX_PLAYERS);
            case VALID -> { }
        }
    }

    private void sendPreview(MinecraftClient client, PendingConfirmation<DemotionRequest> pending) {
        DemotionRequest request = pending.request();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.preview",
                request.usernames().size(), request.rank(), String.join(", ", request.usernames()));
        request.commands().forEach(command -> ChatUtils.sendClientMessage(client, "§8• §7/" + command));
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.instructions",
                "/dmls demowave confirm", "/dmls demowave cancel");
    }

    private void invalidatePending() {
        if (pendingConfirmation != null) pendingConfirmation.invalidate();
        pendingConfirmation = null;
    }
    private static String confirmationError(PendingConfirmation.ConsumeStatus status) {
        return switch (status) {
            case EXPIRED -> "dmls.chat.confirmation.expired";
            case INVALID_TOKEN -> "dmls.chat.confirmation.invalid";
            case ALREADY_CONSUMED, INVALIDATED -> "dmls.chat.confirmation.none";
            case CONFIRMED -> throw new IllegalArgumentException("confirmed is not an error");
        };
    }
}
