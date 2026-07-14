package com.duperknight.client.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacedCommandSequenceTest {
    @Test
    void unknownOutputNeverAdvancesAndConfirmedStepsArePaced() {
        List<String> dispatched = new ArrayList<>();
        PacedCommandSequence<String> sequence = new PacedCommandSequence<>(
                List.of("one", "two"), 2, 5,
                step -> {
                    dispatched.add(step);
                    return CommandDispatch.SENT;
                },
                (step, message) -> switch (message) {
                    case "working" -> ResponseStatus.PROGRESS;
                    case "done" -> ResponseStatus.CONFIRMED;
                    case "no" -> ResponseStatus.REJECTED;
                    default -> ResponseStatus.UNRELATED;
                });

        assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, sequence.start());
        assertEquals(List.of("one"), dispatched);
        assertEquals(ResponseStatus.UNRELATED, sequence.accept("random output"));
        assertEquals(0, sequence.currentIndex());
        sequence.tick();
        assertEquals(ResponseStatus.PROGRESS, sequence.accept("working"));
        assertEquals(ResponseStatus.CONFIRMED, sequence.accept("done"));
        assertEquals(PacedCommandSequence.State.PACING, sequence.state());
        sequence.tick();
        assertEquals(List.of("one"), dispatched);
        sequence.tick();
        assertEquals(List.of("one", "two"), dispatched);
        assertEquals(ResponseStatus.REJECTED, sequence.accept("no"));
        assertEquals(PacedCommandSequence.State.REJECTED, sequence.state());
        assertEquals(1, sequence.confirmedCount());
        assertEquals(1, sequence.processedCount());
    }

    @Test
    void simulatedStepsCompleteButAreNotReportedAsConfirmed() {
        PacedCommandSequence<String> sequence = new PacedCommandSequence<>(
                List.of("one", "two", "three"), 0, 5,
                ignored -> CommandDispatch.SIMULATED,
                (step, message) -> ResponseStatus.CONFIRMED);

        assertEquals(PacedCommandSequence.State.COMPLETED, sequence.start());
        assertEquals(3, sequence.processedCount());
        assertEquals(3, sequence.simulatedCount());
        assertEquals(0, sequence.confirmedCount());
    }

    @Test
    void blockedDispatchAndTimeoutFailClosed() {
        PacedCommandSequence<String> blocked = new PacedCommandSequence<>(
                List.of("one"), 0, 2,
                ignored -> CommandDispatch.BLOCKED,
                (step, message) -> ResponseStatus.CONFIRMED);
        assertEquals(PacedCommandSequence.State.BLOCKED, blocked.start());

        PacedCommandSequence<String> timedOut = new PacedCommandSequence<>(
                List.of("one"), 0, 2,
                ignored -> CommandDispatch.SENT,
                (step, message) -> ResponseStatus.UNRELATED);
        timedOut.start();
        assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, timedOut.tick());
        assertEquals(PacedCommandSequence.State.TIMED_OUT, timedOut.tick());
        assertEquals(0, timedOut.processedCount());
    }

    @Test
    void parserFailureAndCancellationAreTerminal() {
        PacedCommandSequence<String> failed = new PacedCommandSequence<>(
                List.of("one"), 0, 2,
                ignored -> CommandDispatch.SENT,
                (step, message) -> { throw new IllegalArgumentException("fixture"); });
        failed.start();
        assertEquals(ResponseStatus.REJECTED, failed.accept("anything"));
        assertEquals(PacedCommandSequence.State.FAILED, failed.state());

        PacedCommandSequence<String> cancelled = new PacedCommandSequence<>(
                List.of("one"), 0, 2,
                ignored -> CommandDispatch.SENT,
                (step, message) -> ResponseStatus.CONFIRMED);
        cancelled.start();
        cancelled.cancel();
        assertEquals(PacedCommandSequence.State.CANCELLED, cancelled.state());
        assertEquals(ResponseStatus.UNRELATED, cancelled.accept("done"));
    }
}
