package com.duperknight.client.modules;

/** Persisted settings owned by the Mini Me HUD module. */
public record MiniMeHudPreferences(
        boolean dupeyHud,
        boolean siaffyHud,
        boolean beanyHud,
        boolean morvyHud,
        boolean biggyHud,
        boolean chaosMode
) {
    public static MiniMeHudPreferences defaults() {
        return new MiniMeHudPreferences(false, false, false, false, false, false);
    }

    public MiniMeHudPreferences withDupeyHud(boolean enabled) {
        return new MiniMeHudPreferences(enabled, siaffyHud, beanyHud, morvyHud, biggyHud, chaosMode);
    }

    public MiniMeHudPreferences withSiaffyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, enabled, beanyHud, morvyHud, biggyHud, chaosMode);
    }

    public MiniMeHudPreferences withBeanyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, enabled, morvyHud, biggyHud, chaosMode);
    }

    public MiniMeHudPreferences withMorvyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, enabled, biggyHud, chaosMode);
    }

    public MiniMeHudPreferences withBiggyHud(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, enabled, chaosMode);
    }

    public MiniMeHudPreferences withChaosMode(boolean enabled) {
        return new MiniMeHudPreferences(dupeyHud, siaffyHud, beanyHud, morvyHud, biggyHud, enabled);
    }
}
