package com.duperknight.client.utils;

import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Controls how the shared DMLS menu header uses vertical space. */
public enum HeaderBehavior {
    ALWAYS_BIG("dmls.option.header_behavior.always_big"),
    ALWAYS_SMALL("dmls.option.header_behavior.always_small"),
    ON_SCROLL("dmls.option.header_behavior.on_scroll");

    private final String translationKey;

    HeaderBehavior(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text displayName() {
        return Text.translatable(translationKey);
    }

    public static List<HeaderBehavior> options() {
        return List.of(values());
    }

    public static HeaderBehavior parse(String value) {
        if (value == null) return ON_SCROLL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ON_SCROLL;
        }
    }
}
