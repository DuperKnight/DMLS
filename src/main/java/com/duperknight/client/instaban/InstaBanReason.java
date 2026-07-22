package com.duperknight.client.instaban;

import java.util.Locale;

public enum InstaBanReason {
    DO_NOT_INSTA_BAN,
    REMOVED,
    EXPLICITLY_BANNED,
    NOT_FOUND,
    VPN,
    UNCLASSIFIED,
    INDEX_STALE,
    UNKNOWN;

    static InstaBanReason parse(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
