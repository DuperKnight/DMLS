package com.duperknight.client.modules.session;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.parser.CoreProtectLookupParser;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCancelResult;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared coordinator-backed lifecycle and report rendering for CoreProtect scans. */
public abstract class AbstractCoreProtectScanModule extends DMLSModule {
    private static final int MAX_ITEMS_PER_LINE = 8;
    private static final int MAX_TIME_LENGTH = 16;
    private static final Pattern TIME = Pattern.compile("(\\d{1,4}[wdhms])+");
    private static final Pattern RADIUS = Pattern.compile("#global|\\d{1,5}");

    private final OperationCoordinator coordinator;
    private final String prefix;
    private final String operationId;
    private final String operationDisplayName;
    private final String translationNamespace;
    private final String cancelCommand;
    private final CoreProtectLookupParser.ScanKind scanKind;

    protected AbstractCoreProtectScanModule(
            String prefix,
            String operationId,
            String operationDisplayName,
            String translationNamespace,
            String cancelCommand,
            CoreProtectLookupParser.ScanKind scanKind
    ) {
        this(OperationCoordinator.global(), prefix, operationId, operationDisplayName,
                translationNamespace, cancelCommand, scanKind);
    }

    AbstractCoreProtectScanModule(
            OperationCoordinator coordinator,
            String prefix,
            String operationId,
            String operationDisplayName,
            String translationNamespace,
            String cancelCommand,
            CoreProtectLookupParser.ScanKind scanKind
    ) {
        super(StaffRank.MODERATOR);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.prefix = requireText(prefix, "prefix");
        this.operationId = requireText(operationId, "operationId");
        this.operationDisplayName = requireText(operationDisplayName, "operationDisplayName");
        this.translationNamespace = requireText(translationNamespace, "translationNamespace");
        this.cancelCommand = requireText(cancelCommand, "cancelCommand");
        this.scanKind = Objects.requireNonNull(scanKind, "scanKind");
    }

    @Override
    public final void register() {
        // One coordinator owns the sole tick and router subscriptions for both scans.
        coordinator.register();
    }

    /** Starts a validated scan from either the command or GUI entrypoint. */
    public final SubmissionResult submit(MinecraftClient client, String ign, String time, String radius) {
        if (!hasRequiredRank(client)) {
            return SubmissionResult.RANK_BLOCKED;
        }

        Preparation prepared = prepare(scanKind, ign, time, radius);
        if (!prepared.valid()) {
            String key = switch (prepared.status()) {
                case INVALID_IGN -> "dmls.chat.common.invalid_ign";
                case INVALID_TIME -> "dmls.validation.co.time";
                case INVALID_RADIUS -> "dmls.validation.co.radius";
                case VALID -> throw new IllegalStateException("valid preparation without request");
            };
            ChatUtils.sendTranslatedMessage(client, prefix, key);
            return switch (prepared.status()) {
                case INVALID_IGN -> SubmissionResult.INVALID_IGN;
                case INVALID_TIME -> SubmissionResult.INVALID_TIME;
                case INVALID_RADIUS -> SubmissionResult.INVALID_RADIUS;
                case VALID -> SubmissionResult.FAILED;
            };
        }

        ScanOperation operation = new ScanOperation(prepared.request());
        OperationStartResult started = coordinator.start(
                client, operationId, operationDisplayName, operation);
        if (started != OperationStartResult.STARTED) {
            return handleStartFailure(client, started);
        }
        return switch (operation.initialDispatch) {
            case SENT -> SubmissionResult.STARTED;
            case SIMULATED -> SubmissionResult.SIMULATED;
            case BLOCKED -> SubmissionResult.SERVER_BLOCKED;
        };
    }

    /** Cancels only this module's scan; another module's active operation is left untouched. */
    public final boolean cancel(MinecraftClient client) {
        OperationCancelResult result = coordinator.cancel(operationId, client);
        if (result != OperationCancelResult.CANCELLED) {
            ChatUtils.sendTranslatedMessage(client, prefix, translationNamespace + ".nothing");
        }
        return result == OperationCancelResult.CANCELLED;
    }

    public static Preparation prepare(
            CoreProtectLookupParser.ScanKind kind,
            String ign,
            String time,
            String radius
    ) {
        Objects.requireNonNull(kind, "kind");
        String cleanIgn = value(ign).trim();
        String target = cleanIgn.isEmpty() || cleanIgn.equals("*") ? "*" : cleanIgn;
        if (!target.equals("*") && !InputValidators.isUsername(target)) {
            return Preparation.error(PreparationStatus.INVALID_IGN);
        }

        String cleanTime = value(time).trim().toLowerCase(Locale.ROOT);
        if (cleanTime.length() > MAX_TIME_LENGTH || !TIME.matcher(cleanTime).matches()) {
            return Preparation.error(PreparationStatus.INVALID_TIME);
        }

        String cleanRadius = value(radius).trim().toLowerCase(Locale.ROOT);
        if (!RADIUS.matcher(cleanRadius).matches()) {
            return Preparation.error(PreparationStatus.INVALID_RADIUS);
        }

        String action = kind == CoreProtectLookupParser.ScanKind.CONTAINER ? "container" : "block";
        String command = "co lookup" + (target.equals("*") ? "" : " u:" + target)
                + " t:" + cleanTime + " r:" + cleanRadius + " a:" + action;
        return new Preparation(PreparationStatus.VALID,
                new ScanRequest(kind, target, cleanTime, cleanRadius, command));
    }

    private SubmissionResult handleStartFailure(MinecraftClient client, OperationStartResult started) {
        return switch (started) {
            case BUSY -> {
                String activeName = coordinator.activeDescriptor()
                        .map(descriptor -> descriptor.displayName())
                        .orElse("another operation");
                ChatUtils.sendTranslatedMessage(client, prefix, "dmls.chat.scan.busy", activeName);
                yield SubmissionResult.BUSY;
            }
            case SERVER_BLOCKED -> {
                ServerGuard.GuardResult guard = ServerGuard.check(client);
                ChatUtils.sendTranslatedMessage(client, prefix, "dmls.chat.server_guard.blocked",
                        guard.reason(), guard.address());
                yield SubmissionResult.SERVER_BLOCKED;
            }
            case INVALID, FAILED_TO_START -> {
                ChatUtils.sendTranslatedMessage(client, prefix, "dmls.chat.scan.start_failed");
                yield SubmissionResult.FAILED;
            }
            case STARTED -> throw new IllegalStateException("STARTED is not a failure");
        };
    }

    private void report(MinecraftClient client, ScanRequest request, CoreProtectScanSession.Snapshot snapshot) {
        if (client == null) {
            return;
        }

        String header = Text.translatable(translationNamespace + ".header",
                request.target(), request.time(), request.radius()).getString();
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));

        if (snapshot.aggregates().isEmpty()) {
            String emptyKey = switch (snapshot.completion()) {
                case COMPLETE, NO_RESULTS -> translationNamespace + ".no_results";
                case TIMEOUT -> translationNamespace + ".no_response";
                default -> "dmls.chat.scan.no_partial_results";
            };
            ChatUtils.sendTranslatedMessage(client, "", emptyKey);
        } else {
            grouped(snapshot.aggregates()).entrySet().stream()
                    .sorted(Comparator.comparingLong(
                            (Map.Entry<String, Map<String, Map<String, Long>>> entry) -> total(entry.getValue()))
                            .reversed())
                    .forEach(playerEntry -> playerEntry.getValue().forEach((action, materials) ->
                            ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(
                                    translationNamespace + "." + action,
                                    playerEntry.getKey(), itemsSummary(materials))))));
        }

        String pagesKey = snapshot.completion() == CoreProtectScanSession.Completion.PAGE_LIMIT
                ? translationNamespace + ".pages.capped"
                : translationNamespace + ".pages";
        ChatUtils.sendTranslatedMessage(client, "", pagesKey,
                snapshot.confirmedPages(), snapshot.totalPages());
        sendCompletionDetail(client, snapshot);
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
    }

    private void sendCompletionDetail(MinecraftClient client, CoreProtectScanSession.Snapshot snapshot) {
        switch (snapshot.completion()) {
            case COMPLETE, NO_RESULTS, PAGE_LIMIT, SIMULATED, ACTIVE -> {
            }
            case UNCONFIRMED_PAGE -> ChatUtils.sendTranslatedMessage(client, "",
                    "dmls.chat.scan.stop.unconfirmed", snapshot.requestedPage());
            case TIMEOUT -> ChatUtils.sendTranslatedMessage(client, "",
                    "dmls.chat.scan.stop.timeout", snapshot.requestedPage());
            case REJECTED -> ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.scan.stop.rejected");
            case MALFORMED -> ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.scan.stop.malformed");
            case TIME_LIMIT -> ChatUtils.sendTranslatedMessage(client, "",
                    "dmls.chat.scan.stop.time_limit", CoreProtectScanSession.MAX_DURATION_TICKS / 20 / 60);
            case AGGREGATE_LIMIT -> ChatUtils.sendTranslatedMessage(client, "",
                    "dmls.chat.scan.stop.aggregate_limit", CoreProtectScanSession.MAX_AGGREGATES);
            case DISPATCH_BLOCKED -> ChatUtils.sendTranslatedMessage(client, "",
                    "dmls.chat.scan.stop.dispatch", snapshot.requestedPage());
            case CANCELLED -> ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.scan.stop.cancelled");
            case CONNECTION_CHANGED -> ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.scan.stop.connection");
            case INTERNAL_ERROR -> ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.scan.stop.internal");
        }
    }

    private static Map<String, Map<String, Map<String, Long>>> grouped(
            List<CoreProtectScanSession.Aggregate> aggregates
    ) {
        Map<String, Map<String, Map<String, Long>>> grouped = new LinkedHashMap<>();
        for (CoreProtectScanSession.Aggregate aggregate : aggregates) {
            grouped.computeIfAbsent(aggregate.player(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(aggregate.action(), ignored -> new LinkedHashMap<>())
                    .put(aggregate.material(), aggregate.count());
        }
        return grouped;
    }

    private static long total(Map<String, Map<String, Long>> actions) {
        long total = 0;
        for (Map<String, Long> materials : actions.values()) {
            for (long count : materials.values()) {
                if (Long.MAX_VALUE - total < count) {
                    return Long.MAX_VALUE;
                }
                total += count;
            }
        }
        return total;
    }

    private static String itemsSummary(Map<String, Long> materials) {
        List<String> parts = new ArrayList<>();
        materials.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_ITEMS_PER_LINE)
                .forEach(entry -> parts.add("x" + entry.getValue() + " " + entry.getKey()));
        int remaining = materials.size() - MAX_ITEMS_PER_LINE;
        if (remaining > 0) {
            parts.add(Text.translatable("dmls.chat.scan.more", remaining).getString());
        }
        return String.join(", ", parts);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name);
        return value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public enum PreparationStatus { VALID, INVALID_IGN, INVALID_TIME, INVALID_RADIUS }

    public record Preparation(PreparationStatus status, ScanRequest request) {
        public boolean valid() {
            return status == PreparationStatus.VALID;
        }

        private static Preparation error(PreparationStatus status) {
            return new Preparation(status, null);
        }
    }

    public record ScanRequest(
            CoreProtectLookupParser.ScanKind kind,
            String target,
            String time,
            String radius,
            String command
    ) {
    }

    public enum SubmissionResult {
        STARTED,
        SIMULATED,
        INVALID_IGN,
        INVALID_TIME,
        INVALID_RADIUS,
        RANK_BLOCKED,
        SERVER_BLOCKED,
        BUSY,
        FAILED;

        public boolean accepted() {
            return this == STARTED || this == SIMULATED;
        }
    }

    private final class ScanOperation implements ManagedOperation {
        private final ScanRequest request;
        private final CoreProtectScanSession session;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;
        private boolean reported;

        private ScanOperation(ScanRequest request) {
            this.request = request;
            this.session = new CoreProtectScanSession(request.kind(), request.target());
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            initialDispatch = handle.dispatchCommand(client, request.command());
            switch (initialDispatch) {
                case SENT -> ChatUtils.sendTranslatedMessage(client, prefix,
                        translationNamespace + ".start", request.time(), request.radius(), cancelCommand);
                case SIMULATED -> {
                    session.stop(CoreProtectScanSession.Completion.SIMULATED);
                    reported = true;
                    handle.complete();
                    ChatUtils.sendTranslatedMessage(client, prefix,
                            "dmls.chat.scan.simulated", "/" + request.command());
                }
                case BLOCKED -> {
                    session.stop(CoreProtectScanSession.Completion.DISPATCH_BLOCKED);
                    reported = true;
                    handle.complete();
                    ChatUtils.sendTranslatedMessage(client, prefix, "dmls.chat.command.not_sent");
                }
            }
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            apply(handle, client, session.tick());
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
            if (message.origin() != MessageOrigin.SERVER_SYSTEM) {
                return;
            }
            apply(handle, client, session.accept(message.cleanText()));
        }

        @Override
        public void onCancelled(
                OperationHandle handle,
                MinecraftClient client,
                OperationCancelReason reason
        ) {
            if (reported) return;
            CoreProtectScanSession.Completion completion = switch (reason) {
                case USER_REQUESTED, MODULE_REQUESTED -> CoreProtectScanSession.Completion.CANCELLED;
                case CONNECTION_CHANGED -> CoreProtectScanSession.Completion.CONNECTION_CHANGED;
                case DISPATCH_BLOCKED -> CoreProtectScanSession.Completion.DISPATCH_BLOCKED;
                case INTERNAL_ERROR -> CoreProtectScanSession.Completion.INTERNAL_ERROR;
            };
            session.stop(completion);
            reported = true;
            if (completion == CoreProtectScanSession.Completion.CANCELLED) {
                ChatUtils.sendTranslatedMessage(client, prefix, translationNamespace + ".cancelled");
            } else if (completion == CoreProtectScanSession.Completion.CONNECTION_CHANGED) {
                ChatUtils.sendTranslatedMessage(client, prefix, "dmls.chat.session.cancelled");
            }
            report(client, request, session.snapshot());
        }

        private void apply(
                OperationHandle handle,
                MinecraftClient client,
                CoreProtectScanSession.Action action
        ) {
            if (action.type() == CoreProtectScanSession.ActionType.NONE || reported) {
                return;
            }
            if (action.type() == CoreProtectScanSession.ActionType.REQUEST_PAGE) {
                CommandDispatch dispatch = handle.dispatchCommand(client, "co page " + action.page());
                if (dispatch == CommandDispatch.SENT) {
                    session.pageRequested(action.page());
                    if (action.page() % 5 == 0) {
                        ChatUtils.sendTranslatedMessage(client, prefix, translationNamespace + ".progress",
                                action.page(), session.snapshot().totalPages());
                    }
                } else {
                    finishAndReport(handle, client,
                            dispatch == CommandDispatch.SIMULATED
                                    ? CoreProtectScanSession.Completion.SIMULATED
                                    : CoreProtectScanSession.Completion.DISPATCH_BLOCKED);
                }
                return;
            }
            finishAndReport(handle, client, action.completion());
        }

        private void finishAndReport(
                OperationHandle handle,
                MinecraftClient client,
                CoreProtectScanSession.Completion completion
        ) {
            session.stop(completion);
            reported = true;
            handle.complete();
            if (completion == CoreProtectScanSession.Completion.SIMULATED) {
                ChatUtils.sendTranslatedMessage(client, prefix,
                        "dmls.chat.scan.simulated", "/co page " + session.snapshot().requestedPage());
                return;
            }
            report(client, request, session.snapshot());
        }
    }
}
