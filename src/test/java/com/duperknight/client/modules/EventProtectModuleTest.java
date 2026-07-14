package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventProtectModuleTest {
    @Test
    void acceptsAndTrimsAPlainOneToSixtyFourCodePointName() {
        assertEquals("Wither Fight", EventProtectModule.validateEventName("  Wither Fight  ").orElseThrow());
        assertTrue(EventProtectModule.validateEventName("😀".repeat(64)).isPresent());
        assertTrue(EventProtectModule.validateEventName("x".repeat(65)).isEmpty());
    }

    @Test
    void rejectsBlankFormattingAndControlCharacters() {
        assertTrue(EventProtectModule.validateEventName("   ").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event &4Red").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event §4Red").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event\nban Player").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event\u0000Name").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event\u200BName").isEmpty());
        assertTrue(EventProtectModule.validateEventName("Event\u2028Name").isEmpty());
    }

    @Test
    void buildsTheExactProtectionCommandFromTheValidatedSubmission() {
        assertEquals("broadcastraw public &7&lWither Fight is starting, and is protected by staff: "
                        + "bandits and raiders are &4&lnot&7&l allowed to interfere.",
                EventProtectModule.buildBroadcastCommand("Wither Fight"));
    }
}
