package com.duperknight.client.moderation;

/** Persisted presentation/filter preferences for the moderation view. */
public record ModerationPreferences(
        boolean includeLocal,
        boolean includeTrade,
        boolean includeRp,
        boolean includeStaff,
        boolean includeAdmin,
        boolean includeServer,
        boolean showTimestamps,
        boolean highlightAlerts
) {
    public static ModerationPreferences defaults() {
        return new ModerationPreferences(false, false, false, false, false, true, true, false);
    }

    public boolean includesInGlobal(ChatChannel channel) {
        return switch (channel) {
            case GLOBAL -> true;
            case LOCAL -> includeLocal;
            case TRADE -> includeTrade;
            case RP -> includeRp;
            case STAFF -> includeStaff;
            case ADMIN -> includeAdmin;
            case SERVER -> includeServer;
        };
    }

    public ModerationPreferences withChannel(ChatChannel channel, boolean included) {
        return switch (channel) {
            case GLOBAL -> this;
            case LOCAL -> new ModerationPreferences(included, includeTrade, includeRp, includeStaff, includeAdmin,
                    includeServer, showTimestamps, highlightAlerts);
            case TRADE -> new ModerationPreferences(includeLocal, included, includeRp, includeStaff, includeAdmin,
                    includeServer, showTimestamps, highlightAlerts);
            case RP -> new ModerationPreferences(includeLocal, includeTrade, included, includeStaff, includeAdmin,
                    includeServer, showTimestamps, highlightAlerts);
            case STAFF -> new ModerationPreferences(includeLocal, includeTrade, includeRp, included, includeAdmin,
                    includeServer, showTimestamps, highlightAlerts);
            case ADMIN -> new ModerationPreferences(includeLocal, includeTrade, includeRp, includeStaff, included,
                    includeServer, showTimestamps, highlightAlerts);
            case SERVER -> new ModerationPreferences(includeLocal, includeTrade, includeRp, includeStaff, includeAdmin,
                    included, showTimestamps, highlightAlerts);
        };
    }

    public ModerationPreferences withTimestamps(boolean enabled) {
        return new ModerationPreferences(includeLocal, includeTrade, includeRp, includeStaff, includeAdmin,
                includeServer, enabled, highlightAlerts);
    }

    public ModerationPreferences withHighlightAlerts(boolean enabled) {
        return new ModerationPreferences(includeLocal, includeTrade, includeRp, includeStaff, includeAdmin,
                includeServer, showTimestamps, enabled);
    }
}
