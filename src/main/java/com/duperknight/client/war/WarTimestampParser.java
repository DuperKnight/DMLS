package com.duperknight.client.war;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the Unix-seconds value from raw or Discord-formatted timestamps. */
public final class WarTimestampParser {
    private static final Pattern DISCORD_TIMESTAMP =
            Pattern.compile("<t:(\\d{1,12})(?::[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_TIMESTAMP =
            Pattern.compile("(?<!\\d)(\\d{1,12})(?!\\d)");

    private WarTimestampParser() {
    }

    public static OptionalLong parseEpochSeconds(String input) {
        String value = Objects.requireNonNullElse(input, "").trim();
        if (value.isEmpty()) return OptionalLong.empty();

        Matcher discord = DISCORD_TIMESTAMP.matcher(value);
        if (discord.find()) return parseSeconds(discord.group(1));

        Matcher raw = RAW_TIMESTAMP.matcher(value);
        if (raw.find()) return parseSeconds(raw.group(1));
        return OptionalLong.empty();
    }

    public static OptionalLong parseEpochMillis(String input) {
        OptionalLong seconds = parseEpochSeconds(input);
        if (seconds.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Math.multiplyExact(seconds.getAsLong(), 1000L));
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong parseSeconds(String digits) {
        try {
            long seconds = Long.parseLong(digits);
            Instant.ofEpochSecond(seconds);
            return OptionalLong.of(seconds);
        } catch (NumberFormatException | DateTimeException ignored) {
            return OptionalLong.empty();
        }
    }
}
