package com.duperknight.client.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModerationPreferencesTest {
    @Test
    void defaultsAlwaysIncludeGlobalAndServerOnly() {
        ModerationPreferences preferences = ModerationPreferences.defaults();
        assertTrue(preferences.includesInGlobal(ChatChannel.GLOBAL));
        assertTrue(preferences.includesInGlobal(ChatChannel.SERVER));
        assertFalse(preferences.includesInGlobal(ChatChannel.TRADE));
        assertTrue(preferences.showTimestamps());
        assertFalse(preferences.highlightAlerts());
    }
}
