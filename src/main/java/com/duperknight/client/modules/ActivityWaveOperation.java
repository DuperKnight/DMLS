package com.duperknight.client.modules;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.ActivityResponseParser;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.ResponseStatus;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Managed, partial-result-preserving lifecycle for an activity wave. */
final class ActivityWaveOperation implements ManagedOperation {
    static final int COMMAND_GAP_TICKS = 20;
    static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;

    enum ResultKind { HOURS, NO_RESPONSE, SIMULATED }

    record ActivityValue(ResultKind kind, double hours) {
        static ActivityValue hours(double hours) {
            return new ActivityValue(ResultKind.HOURS, hours);
        }

        static ActivityValue noResponse() {
            return new ActivityValue(ResultKind.NO_RESPONSE, 0);
        }

        static ActivityValue simulated() {
            return new ActivityValue(ResultKind.SIMULATED, 0);
        }
    }

    record Summary(
            Map<String, ActivityValue> results,
            int totalPlayers,
            int reportedDays,
            boolean completed,
            OperationCancelReason cancelReason
    ) {
    }

    interface Listener {
        void started(MinecraftClient client, int playerCount);

        void progress(MinecraftClient client, String username, int playerIndex, int playerCount);

        void finished(MinecraftClient client, Summary summary);

        void cancelled(MinecraftClient client, Summary summary, OperationCancelReason reason);
    }

    @FunctionalInterface
    interface Dispatcher {
        CommandDispatch dispatch(OperationHandle handle, MinecraftClient client, String command);
    }

    private final List<String> usernames;
    private final Listener listener;
    private final Dispatcher dispatcher;
    private final Map<String, ActivityValue> results = new LinkedHashMap<>();

    private OperationHandle handle;
    private MinecraftClient client;
    private PacedCommandSequence<String> sequence;
    private ActivityResponseParser parser;
    private ActivityResponseParser.ActivityResult parsedResult;
    private int playerIndex;
    private int gapTicks;
    private int reportedDays = 30;
    private boolean waitingForGap;
    private boolean finished;

    ActivityWaveOperation(List<String> usernames, Listener listener) {
        this(usernames, listener, (handle, client, command) -> handle.dispatchCommand(client, command));
    }

    ActivityWaveOperation(List<String> usernames, Listener listener, Dispatcher dispatcher) {
        this.usernames = List.copyOf(Objects.requireNonNull(usernames, "usernames"));
        if (this.usernames.isEmpty()) throw new IllegalArgumentException("usernames");
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
        listener.started(client, usernames.size());
        startCurrentPlayer();
    }

    @Override
    public void onTick(OperationHandle handle, MinecraftClient client) {
        if (finished) return;
        this.client = client;
        if (waitingForGap) {
            if (++gapTicks >= COMMAND_GAP_TICKS) {
                waitingForGap = false;
                startCurrentPlayer();
            }
            return;
        }

        sequence.tick();
        evaluateSequence();
    }

    @Override
    public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
        if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
        if (finished || waitingForGap || sequence == null) return;
        this.client = client;
        sequence.accept(message.cleanText());
        evaluateSequence();
    }

    @Override
    public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
        if (finished) return;
        finished = true;
        if (sequence != null) sequence.cancel();
        listener.cancelled(client, summary(false, reason), reason);
    }

    private void startCurrentPlayer() {
        if (finished) return;
        if (playerIndex >= usernames.size()) {
            complete();
            return;
        }

        String username = usernames.get(playerIndex);
        parser = new ActivityResponseParser(username);
        parsedResult = null;
        listener.progress(client, username, playerIndex + 1, usernames.size());
        sequence = new PacedCommandSequence<>(List.of(username), 0, RESPONSE_TIMEOUT_TICKS,
                ignored -> dispatcher.dispatch(handle, client, "activity " + username),
                (ignored, line) -> parse(line));
        sequence.start();
        evaluateSequence();
    }

    private ResponseStatus parse(String line) {
        ActivityResponseParser.ParseResult parsed = parser.parse(line);
        return switch (parsed.status()) {
            case UNRELATED -> ResponseStatus.UNRELATED;
            case PROGRESS -> ResponseStatus.PROGRESS;
            case CONFIRMED -> {
                parsedResult = parsed.result().orElseThrow();
                yield ResponseStatus.CONFIRMED;
            }
            case MALFORMED -> ResponseStatus.REJECTED;
        };
    }

    private void evaluateSequence() {
        if (finished || sequence == null) return;
        switch (sequence.state()) {
            case COMPLETED -> {
                String username = usernames.get(playerIndex);
                if (sequence.simulatedCount() > 0) {
                    results.put(username, ActivityValue.simulated());
                } else if (parsedResult != null) {
                    reportedDays = parsedResult.days();
                    results.put(username, ActivityValue.hours(parsedResult.hours()));
                } else {
                    results.put(username, ActivityValue.noResponse());
                }
                advance(true);
            }
            case TIMED_OUT, REJECTED, FAILED -> {
                results.put(usernames.get(playerIndex), ActivityValue.noResponse());
                advance(false);
            }
            case BLOCKED -> handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
            case NEW, AWAITING_RESPONSE, PACING, CANCELLED -> { }
        }
    }

    private void advance(boolean useGap) {
        playerIndex++;
        if (playerIndex >= usernames.size()) {
            complete();
        } else if (useGap) {
            waitingForGap = true;
            gapTicks = 0;
        } else {
            startCurrentPlayer();
        }
    }

    private void complete() {
        if (finished) return;
        finished = true;
        Summary summary = summary(true, null);
        handle.complete();
        listener.finished(client, summary);
    }

    private Summary summary(boolean completed, OperationCancelReason reason) {
        Map<String, ActivityValue> copy = Collections.unmodifiableMap(new LinkedHashMap<>(results));
        return new Summary(copy, usernames.size(), reportedDays, completed, reason);
    }
}
