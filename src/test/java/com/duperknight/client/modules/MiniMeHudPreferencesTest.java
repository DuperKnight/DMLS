package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMeHudPreferencesTest {
    @Test
    void defaultsDisableEveryMiniMeAndChaosMode() {
        MiniMeHudPreferences preferences = MiniMeHudPreferences.defaults();
        assertFalse(preferences.dupeyHud());
        assertFalse(preferences.siaffyHud());
        assertFalse(preferences.beanyHud());
        assertFalse(preferences.morvyHud());
        assertFalse(preferences.biggyHud());
        assertFalse(preferences.chaosMode());
    }

    @Test
    void togglesAreIndependent() {
        MiniMeHudPreferences preferences = MiniMeHudPreferences.defaults()
                .withDupeyHud(true)
                .withChaosMode(true);
        assertTrue(preferences.dupeyHud());
        assertTrue(preferences.chaosMode());
        assertFalse(preferences.siaffyHud());
        assertFalse(preferences.biggyHud());
    }
}
