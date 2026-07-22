package com.duperknight.client.instaban;

import java.util.Locale;

public enum InstaBanStatus {
    SAFE,
    UNSAFE,
    UNSURE;

    static InstaBanStatus parse(String value) {
        if (value == null) return UNSURE;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNSURE;
        }
    }
}
