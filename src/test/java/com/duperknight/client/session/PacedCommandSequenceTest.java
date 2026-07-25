package com.duperknight.client.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacedCommandSequenceTest {
    @Test
    void readinessWaitDoesNotStartResponseTimeout() {
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicInteger dispatches = new AtomicInteger();
        PacedCommandSequence<String> sequence = new PacedCommandSequence<>(
                List.of("first"),
                0,
                2,
                step -> {
                    dispatches.incrementAndGet();
                    return CommandDispatch.SENT;
                },
                (step, message) -> ResponseStatus.CONFIRMED,
                ready::get
        );

        assertEquals(PacedCommandSequence.State.PACING, sequence.start());
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(PacedCommandSequence.State.PACING, sequence.tick());
        }
        assertEquals(0, dispatches.get());

        ready.set(true);
        assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, sequence.tick());
        assertEquals(1, dispatches.get());
        assertEquals(ResponseStatus.CONFIRMED, sequence.accept("confirmed"));
        assertEquals(PacedCommandSequence.State.COMPLETED, sequence.state());
    }

    @Test
    void fixedGapAndReadinessAreBothRequired() {
        AtomicBoolean ready = new AtomicBoolean(true);
        AtomicInteger dispatches = new AtomicInteger();
        PacedCommandSequence<String> sequence = new PacedCommandSequence<>(
                List.of("first", "second"),
                2,
                20,
                step -> {
                    dispatches.incrementAndGet();
                    return CommandDispatch.SENT;
                },
                (step, message) -> ResponseStatus.CONFIRMED,
                ready::get
        );

        sequence.start();
        ready.set(false);
        sequence.accept("first confirmed");
        sequence.tick();
        sequence.tick();
        sequence.tick();
        assertEquals(PacedCommandSequence.State.PACING, sequence.state());
        assertEquals(1, dispatches.get());

        ready.set(true);
        assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, sequence.tick());
        assertEquals(2, dispatches.get());
    }
}
