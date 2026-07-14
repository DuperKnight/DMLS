package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreeterModuleTest {
    @Test
    void parsesKnownFirstJoinBroadcastsWithoutMatchingOrdinaryJoins() {
        assertEquals("Alice", GreeterModule.parseFirstJoin("Welcome, Alice, to Stoneworks!").orElseThrow());
        assertEquals("Arthur", GreeterModule.parseFirstJoin("Welcome Arthur to Abexilas!").orElseThrow());
        assertEquals("Carol", GreeterModule.parseFirstJoin("Welcome [Carol] to Abexilas!").orElseThrow());
        assertEquals("Bob_1", GreeterModule.parseFirstJoin("Bob_1 has joined us for the first time").orElseThrow());
        assertTrue(GreeterModule.parseFirstJoin("Alice joined the game").isEmpty());
        assertTrue(GreeterModule.parseFirstJoin("Welcome Alice back to Stoneworks!").isEmpty());
    }

    @Test
    void suppressesRecentDuplicatePromptsAndPrunesAtCooldownBoundary() {
        GreeterModule module = new GreeterModule(() -> 0L);
        long start = 10_000L;

        assertTrue(module.recordPrompt("Alice", start));
        assertFalse(module.recordPrompt("alice", start + 299_999L));
        assertTrue(module.recordPrompt("ALICE", start + 300_000L));
    }
}
