package com.duperknight.client.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Pure state machine for an ordered, response-confirmed command sequence.
 *
 * <p>Live commands never advance on unknown output. Simulated commands are paced but recorded
 * separately from confirmed commands, so callers cannot accidentally report dry-run work as
 * server-confirmed.</p>
 */
public final class PacedCommandSequence<T> {
    public enum State {
        NEW,
        AWAITING_RESPONSE,
        PACING,
        COMPLETED,
        REJECTED,
        TIMED_OUT,
        BLOCKED,
        CANCELLED,
        FAILED;

        public boolean terminal() {
            return switch (this) {
                case COMPLETED, REJECTED, TIMED_OUT, BLOCKED, CANCELLED, FAILED -> true;
                default -> false;
            };
        }
    }

    private final List<T> steps;
    private final int commandGapTicks;
    private final int responseTimeoutTicks;
    private final Function<T, CommandDispatch> dispatcher;
    private final BiFunction<T, String, ResponseStatus> parser;

    private State state = State.NEW;
    private int currentIndex;
    private int responseTicks;
    private int pacingTicks;
    private int processedCount;
    private int confirmedCount;
    private int simulatedCount;
    private CommandDispatch lastDispatch;

    public PacedCommandSequence(
            List<T> steps,
            int commandGapTicks,
            int responseTimeoutTicks,
            Function<T, CommandDispatch> dispatcher,
            BiFunction<T, String, ResponseStatus> parser
    ) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (commandGapTicks < 0) throw new IllegalArgumentException("commandGapTicks");
        if (responseTimeoutTicks < 1) throw new IllegalArgumentException("responseTimeoutTicks");
        this.commandGapTicks = commandGapTicks;
        this.responseTimeoutTicks = responseTimeoutTicks;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public State start() {
        if (state != State.NEW) return state;
        if (steps.isEmpty()) {
            state = State.COMPLETED;
            return state;
        }
        dispatchCurrent();
        return state;
    }

    public State tick() {
        if (state == State.AWAITING_RESPONSE) {
            if (++responseTicks >= responseTimeoutTicks) state = State.TIMED_OUT;
        } else if (state == State.PACING) {
            if (pacingTicks <= 0 || --pacingTicks <= 0) dispatchCurrent();
        }
        return state;
    }

    public ResponseStatus accept(String message) {
        if (state != State.AWAITING_RESPONSE) return ResponseStatus.UNRELATED;

        ResponseStatus result;
        try {
            result = parser.apply(steps.get(currentIndex), Objects.requireNonNullElse(message, ""));
        } catch (RuntimeException exception) {
            state = State.FAILED;
            return ResponseStatus.REJECTED;
        }
        if (result == null) {
            state = State.FAILED;
            return ResponseStatus.REJECTED;
        }

        switch (result) {
            case UNRELATED -> { }
            case PROGRESS -> responseTicks = 0;
            case CONFIRMED -> {
                confirmedCount++;
                advance();
            }
            case REJECTED -> state = State.REJECTED;
        }
        return result;
    }

    public void cancel() {
        if (!state.terminal()) state = State.CANCELLED;
    }

    public State state() {
        return state;
    }

    public Optional<T> currentStep() {
        if (currentIndex >= steps.size() || state == State.NEW || state == State.COMPLETED) {
            return Optional.empty();
        }
        return Optional.of(steps.get(currentIndex));
    }

    public int currentIndex() {
        return currentIndex;
    }

    /** Number of confirmed or simulated steps that were processed. */
    public int processedCount() {
        return processedCount;
    }

    public int confirmedCount() {
        return confirmedCount;
    }

    public int simulatedCount() {
        return simulatedCount;
    }

    public CommandDispatch lastDispatch() {
        return lastDispatch;
    }

    private void dispatchCurrent() {
        responseTicks = 0;
        try {
            lastDispatch = Objects.requireNonNull(dispatcher.apply(steps.get(currentIndex)), "dispatch result");
        } catch (RuntimeException exception) {
            state = State.FAILED;
            return;
        }

        switch (lastDispatch) {
            case SENT -> state = State.AWAITING_RESPONSE;
            case SIMULATED -> {
                simulatedCount++;
                advance();
            }
            case BLOCKED -> state = State.BLOCKED;
        }
    }

    private void advance() {
        processedCount++;
        currentIndex++;
        responseTicks = 0;
        if (currentIndex >= steps.size()) {
            state = State.COMPLETED;
            return;
        }
        pacingTicks = commandGapTicks;
        if (pacingTicks == 0) dispatchCurrent();
        else state = State.PACING;
    }
}
