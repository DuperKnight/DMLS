package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TickSpacedCommandQueueTest {
    @Test
    void releasesCommandsExactlyTwoTicksApart() {
        TickSpacedCommandQueue queue = new TickSpacedCommandQueue(List.of("second", "third"), 2);

        assertTrue(queue.tick().isEmpty());
        assertEquals("second", queue.tick().orElseThrow());
        assertTrue(queue.tick().isEmpty());
        assertEquals("third", queue.tick().orElseThrow());
        assertTrue(queue.isEmpty());
    }

    @Test
    void emptyQueueNeverProducesACommand() {
        TickSpacedCommandQueue queue = new TickSpacedCommandQueue(List.of(), 2);

        assertTrue(queue.tick().isEmpty());
        assertTrue(queue.isEmpty());
    }

    @Test
    void rejectsNonPositiveTickGaps() {
        boolean threw = false;
        try {
            new TickSpacedCommandQueue(List.of("command"), 0);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }

        assertTrue(threw);
        assertFalse(new TickSpacedCommandQueue(List.of("command"), 1).isEmpty());
    }
}
