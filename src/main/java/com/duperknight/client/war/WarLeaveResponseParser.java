package com.duperknight.client.war;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the three Lands responses that can advance a claim-detachment step. */
public final class WarLeaveResponseParser {
    private static final Pattern LEFT = Pattern.compile(
            "^\\[Lands]\\s+You successfully left nation (.+?) with land (.+?)\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_NATION = Pattern.compile(
            "^\\[Lands]\\s+Your land (.+?) isn't part of any nation\\.",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CAPITAL = Pattern.compile(
            "^\\[Lands]\\s+Your land (.+?) can't leave nation (.+?), because the land is the capital of this nation\\.",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private WarLeaveResponseParser() {
    }

    public static Optional<Result> parse(String message, String expectedLand) {
        String clean = Objects.requireNonNullElse(message, "").trim();
        String expected = Objects.requireNonNullElse(expectedLand, "").trim();
        if (expected.isEmpty()) return Optional.empty();

        Matcher left = LEFT.matcher(firstLine(clean));
        if (left.matches() && same(left.group(2), expected)) {
            return Optional.of(new Result(Type.LEFT, left.group(2).trim(), left.group(1).trim()));
        }

        Matcher none = NO_NATION.matcher(clean);
        if (none.find() && same(none.group(1), expected)) {
            return Optional.of(new Result(Type.NO_NATION, none.group(1).trim(), ""));
        }

        Matcher capital = CAPITAL.matcher(clean);
        if (capital.find() && same(capital.group(1), expected)) {
            return Optional.of(new Result(Type.CAPITAL, capital.group(1).trim(), capital.group(2).trim()));
        }
        return Optional.empty();
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline).trim();
    }

    private static boolean same(String left, String right) {
        return left.trim().toLowerCase(Locale.ROOT).equals(right.trim().toLowerCase(Locale.ROOT));
    }

    public enum Type {
        LEFT,
        NO_NATION,
        CAPITAL
    }

    public record Result(Type type, String land, String nation) {
    }
}
