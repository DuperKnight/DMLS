package com.duperknight.client.modules;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.LuckPermsResponseParser;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.ResponseStatus;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Objects;

/** One-step managed donor permission operation with truthful terminal outcomes. */
final class DonorPetOperation implements ManagedOperation {
    static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;

    enum Outcome {
        CONFIRMED,
        REJECTED,
        SIMULATED,
        SENT_UNVERIFIED,
        BLOCKED,
        CANCELLED
    }

    record Summary(DonorPetModule.DonorPetRequest request, Outcome outcome,
                   PacedCommandSequence.State sequenceState) {
    }

    interface Listener {
        void waiting(MinecraftClient client, DonorPetModule.DonorPetRequest request);

        void finished(MinecraftClient client, Summary summary);

        void cancelled(MinecraftClient client, Summary summary, OperationCancelReason reason);
    }

    @FunctionalInterface
    interface Dispatcher {
        CommandDispatch dispatch(OperationHandle handle, MinecraftClient client, String command);
    }

    private final DonorPetModule.DonorPetRequest request;
    private final Listener listener;
    private final Dispatcher dispatcher;

    private OperationHandle handle;
    private MinecraftClient client;
    private PacedCommandSequence<DonorPetModule.DonorPetRequest> sequence;
    private boolean finished;

    DonorPetOperation(DonorPetModule.DonorPetRequest request, Listener listener) {
        this(request, listener, (handle, client, command) -> handle.dispatchCommand(client, command));
    }

    DonorPetOperation(DonorPetModule.DonorPetRequest request, Listener listener, Dispatcher dispatcher) {
        this.request = Objects.requireNonNull(request, "request");
        if (!request.valid()) throw new IllegalArgumentException("request");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    boolean acceptedAtStart() {
        return sequence != null && sequence.state() != PacedCommandSequence.State.BLOCKED
                && sequence.state() != PacedCommandSequence.State.FAILED;
    }

    @Override
    public void onStarted(OperationHandle handle, MinecraftClient client) {
        this.handle = handle;
        this.client = client;
        sequence = new PacedCommandSequence<>(List.of(request), 0, RESPONSE_TIMEOUT_TICKS,
                ignored -> dispatcher.dispatch(handle, client, request.command()),
                (ignored, line) -> switch (LuckPermsResponseParser.parsePermissionSet(
                        request.username(), request.permission(), line)) {
                    case CONFIRMED -> ResponseStatus.CONFIRMED;
                    case REJECTED -> ResponseStatus.REJECTED;
                    case UNRELATED -> ResponseStatus.UNRELATED;
                });
        sequence.start();
        if (sequence.state() == PacedCommandSequence.State.AWAITING_RESPONSE) {
            listener.waiting(client, request);
        }
        evaluateSequence();
    }

    @Override
    public void onTick(OperationHandle handle, MinecraftClient client) {
        if (finished) return;
        this.client = client;
        sequence.tick();
        evaluateSequence();
    }

    @Override
    public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
        if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
        if (finished) return;
        this.client = client;
        sequence.accept(message.cleanText());
        evaluateSequence();
    }

    @Override
    public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
        if (finished) return;
        finished = true;
        if (sequence != null) sequence.cancel();
        listener.cancelled(client, new Summary(request, Outcome.CANCELLED,
                sequence == null ? PacedCommandSequence.State.CANCELLED : sequence.state()), reason);
    }

    private void evaluateSequence() {
        if (finished) return;
        Outcome outcome = switch (sequence.state()) {
            case COMPLETED -> sequence.simulatedCount() > 0 ? Outcome.SIMULATED : Outcome.CONFIRMED;
            case REJECTED -> Outcome.REJECTED;
            case TIMED_OUT -> Outcome.SENT_UNVERIFIED;
            case BLOCKED, FAILED -> Outcome.BLOCKED;
            case NEW, AWAITING_RESPONSE, PACING, CANCELLED -> null;
        };
        if (outcome == null) return;

        finished = true;
        Summary summary = new Summary(request, outcome, sequence.state());
        handle.complete();
        listener.finished(client, summary);
    }
}
