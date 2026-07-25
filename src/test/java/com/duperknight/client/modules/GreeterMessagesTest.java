package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GreeterMessagesTest {
    @Test
    void blankTemplatesNormalizeToDefaultSelection() {
        assertEquals(List.of(), GreeterMessages.normalizeTemplates(List.of("", "  ")).orElseThrow());
        assertEquals(
                "Welcome to Stoneworks, Alex! Enjoy your stay, and feel free to ask if you have any questions :)",
                GreeterMessages.choose(List.of(), "Alex", new FixedRandom(0)));
    }

    @Test
    void replacesEveryPlayerVariable() {
        assertEquals("Alex, meet Alex!",
                GreeterMessages.format("{player}, meet {player}!", "Alex"));
    }

    @Test
    void selectsFromMultipleCustomMessages() {
        List<String> messages = List.of("First {player}", "Second {player}");

        assertEquals("First Alex", GreeterMessages.choose(messages, "Alex", new FixedRandom(0)));
        assertEquals("Second Alex", GreeterMessages.choose(messages, "Alex", new FixedRandom(1)));
    }

    @Test
    void ignoresInvalidTemplatesLoadedFromAHandEditedConfig() {
        List<String> messages = List.of("Bad {username}", "Hello {player}");

        assertEquals("Hello Alex", GreeterMessages.choose(messages, "Alex", new FixedRandom(0)));
    }

    @Test
    void rejectsUnknownVariablesAndUnsafeText() {
        assertTrue(GreeterMessages.normalizeTemplates(List.of("Hello, {player}!")).isPresent());
        assertFalse(GreeterMessages.normalizeTemplates(List.of("Hello, {username}!")).isPresent());
        assertFalse(GreeterMessages.normalizeTemplates(List.of("Hello, {player-name}!")).isPresent());
        assertFalse(GreeterMessages.normalizeTemplates(List.of("Hello\nthere")).isPresent());
        assertFalse(GreeterMessages.normalizeTemplates(List.of("§cHello")).isPresent());
    }

    @Test
    void rejectsTemplatesThatBecomeTooLongAfterSubstitution() {
        String template = "x".repeat(248) + "{player}";

        assertFalse(GreeterMessages.normalizeTemplates(List.of(template)).isPresent());
    }

    private static final class FixedRandom extends Random {
        private final int index;

        private FixedRandom(int index) {
            this.index = index;
        }

        @Override
        public int nextInt(int bound) {
            return index;
        }
    }
}
