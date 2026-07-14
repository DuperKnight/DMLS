package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.PromoWaveScreen;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.session.PendingConfirmation;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PromoWaveModule extends DMLSModule {
    public static final String OPERATION_ID = "promo-wave";
    public static final int MAX_PLAYERS = 60;
    private static final String PREFIX = "§8[§6DMLS - PromoWave§8] §7";
    private static final Map<String, List<String>> RANK_COMMANDS = new LinkedHashMap<>();

    static {
        RANK_COMMANDS.put("helper", List.of("lp user %s parent add helper"));
        RANK_COMMANDS.put("mod", List.of(
                "lp user %s parent add mod",
                "lp user %s parent remove helper"));
        RANK_COMMANDS.put("sr-mod", List.of(
                "lp user %s parent add sr-mod",
                "lp user %s parent remove mod"));
        RANK_COMMANDS.put("support", List.of("lp user %s parent add support"));
        RANK_COMMANDS.put("admin", List.of(
                "lp user %s parent add admin",
                "lp user %s parent remove sr-mod"));
    }

    private PendingConfirmation<PromotionRequest> pendingConfirmation;

    public PromoWaveModule() {
        super(StaffRank.ADMIN);
    }

    public static List<String> ranks() {
        return List.copyOf(RANK_COMMANDS.keySet());
    }

    public record PromotionRequest(PreparationStatus status, String rank, List<String> usernames,
                                   List<String> skipped, List<String> commands) {
        public boolean valid() { return status == PreparationStatus.VALID; }
    }

    public enum PreparationStatus { VALID, UNKNOWN_RANK, EMPTY, TOO_MANY }
    public enum StageStatus { STAGED, INVALID, BLOCKED, BUSY }

    public record StageResult(StageStatus status, String token, PromotionRequest request) {
        public boolean staged() { return status == StageStatus.STAGED; }
    }

    public static PromotionRequest prepare(String rank, String input) {
        String cleanRank = rank == null ? "" : rank.trim().toLowerCase(Locale.ROOT);
        List<String> templates = RANK_COMMANDS.get(cleanRank);
        if (templates == null) {
            return new PromotionRequest(PreparationStatus.UNKNOWN_RANK, cleanRank, List.of(), List.of(), List.of());
        }
        List<String> skipped = new ArrayList<>();
        List<String> usernames = InputValidators.uniqueUsernames(input, skipped);
        if (usernames.isEmpty()) {
            return new PromotionRequest(PreparationStatus.EMPTY, cleanRank, List.of(), List.copyOf(skipped), List.of());
        }
        if (usernames.size() > MAX_PLAYERS) {
            return new PromotionRequest(PreparationStatus.TOO_MANY, cleanRank, List.copyOf(usernames),
                    List.copyOf(skipped), List.of());
        }
        List<String> commands = usernames.stream()
                .flatMap(username -> templates.stream().map(template -> template.formatted(username)))
                .toList();
        return new PromotionRequest(PreparationStatus.VALID, cleanRank, List.copyOf(usernames),
                List.copyOf(skipped), commands);
    }

    @Override public Text displayName() { return Text.translatable("dmls.module.promo_wave.name"); }
    @Override public ItemStack icon() { return new ItemStack(Items.GOLDEN_HELMET); }
    @Override public List<Text> description() {
        return List.of(Text.translatable("dmls.module.promo_wave.description.1"),
                Text.translatable("dmls.module.promo_wave.description.2"));
    }
    @Override public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PromoWaveScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Compatibility entrypoint: stages a frozen request and never dispatches directly. */
    public StageResult submit(MinecraftClient client, String rank, String input) {
        return stage(client, rank, input);
    }

    public StageResult stage(MinecraftClient client, String rank, String input) {
        return stage(client, rank, input, true);
    }

    public StageResult stage(MinecraftClient client, String rank, String input, boolean announcePreview) {
        invalidatePending();
        PromotionRequest request = prepare(rank, input);
        if (!request.skipped().isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    request.skipped().size() == 1 ? "dmls.chat.promo.skipping.one" : "dmls.chat.promo.skipping.many",
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
        PendingConfirmation<PromotionRequest> pending = pendingConfirmation;
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

        PromotionRequest request = consumed.request().orElseThrow();
        List<RankWaveOperation.Step> steps = new ArrayList<>();
        List<String> templates = RANK_COMMANDS.get(request.rank());
        for (String username : request.usernames()) {
            for (int index = 0; index < templates.size(); index++) {
                String command = templates.get(index).formatted(username);
                steps.add(RankWaveOperation.step(username, command, index == templates.size() - 1));
            }
        }
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
        PendingConfirmation<PromotionRequest> pending = pendingConfirmation;
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

    public List<Text> previewLines(PromotionRequest request) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("dmls.screen.promo.review_summary", request.usernames().size(), request.rank()));
        request.commands().forEach(command -> lines.add(Text.literal("/" + command)));
        return List.copyOf(lines);
    }

    private RankWaveOperation.Listener listener(PromotionRequest request) {
        return new RankWaveOperation.Listener() {
            @Override public void started(MinecraftClient client, int count) {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        count == 1 ? "dmls.chat.promo.start.one" : "dmls.chat.promo.start.many", count, request.rank());
            }

            @Override public void progress(MinecraftClient client, RankWaveOperation.Step step, int index, int total) {
                int playerIndex = request.usernames().indexOf(step.username()) + 1;
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.progress",
                        step.username(), playerIndex, request.usernames().size());
            }

            @Override public void finished(MinecraftClient client, RankWaveOperation.Summary summary) {
                if (summary.state() == com.duperknight.client.session.PacedCommandSequence.State.COMPLETED) {
                    List<String> players = summary.simulatedPlayers().isEmpty()
                            ? summary.confirmedPlayers() : summary.simulatedPlayers();
                    String stem = summary.simulatedPlayers().isEmpty() ? "confirmed" : "simulated";
                    ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.promo." + stem + (players.size() == 1 ? ".one" : ".many"),
                            players.size(), request.rank(), String.join(", ", players));
                } else {
                    reportAbort(client, summary, request, null);
                }
            }

            @Override public void cancelled(MinecraftClient client, RankWaveOperation.Summary summary,
                                             OperationCancelReason reason) {
                reportAbort(client, summary, request, reason);
            }
        };
    }

    private void reportAbort(MinecraftClient client, RankWaveOperation.Summary summary,
                             PromotionRequest request, OperationCancelReason reason) {
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
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo." + suffix,
                completed, request.usernames().size(), interrupted, request.rank());
    }

    private void reportPreparationError(MinecraftClient client, PromotionRequest request) {
        switch (request.status()) {
            case UNKNOWN_RANK -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                    "dmls.chat.promo.unknown_rank", request.rank(), String.join(", ", ranks()));
            case EMPTY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            case TOO_MANY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.too_many", MAX_PLAYERS);
            case VALID -> { }
        }
    }

    private void sendPreview(MinecraftClient client, PendingConfirmation<PromotionRequest> pending) {
        PromotionRequest request = pending.request();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.promo.preview",
                request.usernames().size(), request.rank(), String.join(", ", request.usernames()));
        request.commands().forEach(command -> ChatUtils.sendClientMessage(client, "§8• §7/" + command));
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.confirmation.instructions",
                "/dmls promowave confirm", "/dmls promowave cancel");
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
