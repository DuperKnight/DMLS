package com.duperknight.client.war;

/** Compact command-compatible duration formatting used by War Manager. */
public final class CompactDurationFormatter {
    private CompactDurationFormatter() {
    }

    public static String formatMinutes(int minutes) {
        int safe = Math.max(0, minutes);
        if (safe == 0) return "0m";
        int hours = safe / 60;
        int remainder = safe % 60;
        if (hours == 0) return remainder + "m";
        return remainder == 0 ? hours + "h" : hours + "h" + remainder + "m";
    }

    public static String formatRemaining(long millis) {
        if (millis <= 0) return "0m";
        long totalSeconds = (millis + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder result = new StringBuilder();
        if (hours > 0) result.append(hours).append('h');
        if (minutes > 0) result.append(minutes).append('m');
        if (hours == 0 && seconds > 0) result.append(seconds).append('s');
        return result.isEmpty() ? "0m" : result.toString();
    }
}
