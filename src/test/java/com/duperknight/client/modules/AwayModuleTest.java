package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwayModuleTest {
    @Test
    void startsEveryModuleInstanceOffRegardlessOfPastConfiguration() {
        AwayModule module = new AwayModule(() -> 123_456L);
        assertEquals(AwayModule.Mode.OFF, module.mode());
    }

    @Test
    void parsesDurationsWithoutSilentlyClampingPastTwentyFourHours() {
        assertEquals(5 * 60_000L, AwayModule.parseDurationMillis("5").orElseThrow());
        assertEquals(5_400_000L, AwayModule.parseDurationMillis("1h30m").orElseThrow());
        assertEquals(86_400_000L, AwayModule.parseDurationMillis("24h").orElseThrow());
        assertTrue(AwayModule.parseDurationMillis("24h1s").isEmpty());
        assertTrue(AwayModule.parseDurationMillis("1441").isEmpty());
        assertTrue(AwayModule.parseDurationMillis("0s").isEmpty());
        assertTrue(AwayModule.parseDurationMillis(null).isEmpty());
    }

    @Test
    void rejectedDurationReturnsFailureWithoutChangingSessionState() {
        AwayModule module = new AwayModule(() -> 1_000L);

        assertFalse(module.startBrb(null, "24h1s"));
        assertEquals(AwayModule.Mode.OFF, module.mode());
        assertTrue(module.startBrb(null, "5m"));
        assertEquals(AwayModule.Mode.BRB, module.mode());
    }

    @Test
    void recognizesSupportedPrivateMessageFormatsOnly() {
        assertEquals("Alice", AwayModule.parseIncomingWhisper("[Alice -> me] hello").orElseThrow());
        assertEquals("Bob_1", AwayModule.parseIncomingWhisper("Bob_1 whispers to you: hi").orElseThrow());
        assertEquals("Carol", AwayModule.parseIncomingWhisper("From Carol: question").orElseThrow());
        assertTrue(AwayModule.parseIncomingWhisper("Alice says hello").isEmpty());
    }
}
