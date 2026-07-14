package com.duperknight.client.parser;

import com.duperknight.client.utils.InputValidators;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stateful parser that correlates a two-line /activity response with one requested player. */
public final class ActivityResponseParser {
    private static final Pattern HEADER = Pattern.compile(
            "^activity for player ([A-Za-z0-9_]{3,16}) in the last (\\d+) days[.!]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HOURS = Pattern.compile(
            "^([\\d,]+(?:\\.\\d+)?) minutes or ([\\d,]+(?:\\.\\d+)?) hours[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private final String expectedUsername;
    private Integer days;

    public ActivityResponseParser(String expectedUsername) {
        if (!InputValidators.isUsername(expectedUsername)) {
            throw new IllegalArgumentException("expectedUsername");
        }
        this.expectedUsername = expectedUsername;
    }

    public ParseResult parse(String line) {
        String value = line == null ? "" : line.trim();
        Matcher header = HEADER.matcher(value);
        if (header.matches()) {
            if (!header.group(1).equalsIgnoreCase(expectedUsername)) {
                return ParseResult.unrelated();
            }
            try {
                days = Integer.parseInt(header.group(2));
                return ParseResult.progress();
            } catch (NumberFormatException exception) {
                days = null;
                return ParseResult.malformed();
            }
        }

        if (days == null) {
            return ParseResult.unrelated();
        }
        Matcher hours = HOURS.matcher(value);
        if (!hours.matches()) {
            return ParseResult.unrelated();
        }
        try {
            double parsedHours = Double.parseDouble(hours.group(2).replace(",", ""));
            if (!Double.isFinite(parsedHours) || parsedHours < 0) return ParseResult.malformed();
            return ParseResult.confirmed(new ActivityResult(expectedUsername, days, parsedHours));
        } catch (NumberFormatException exception) {
            return ParseResult.malformed();
        }
    }

    public enum Status { UNRELATED, PROGRESS, CONFIRMED, MALFORMED }

    public record ActivityResult(String username, int days, double hours) {
    }

    public record ParseResult(Status status, Optional<ActivityResult> result) {
        private static ParseResult unrelated() { return new ParseResult(Status.UNRELATED, Optional.empty()); }
        private static ParseResult progress() { return new ParseResult(Status.PROGRESS, Optional.empty()); }
        private static ParseResult malformed() { return new ParseResult(Status.MALFORMED, Optional.empty()); }
        private static ParseResult confirmed(ActivityResult result) {
            return new ParseResult(Status.CONFIRMED, Optional.of(result));
        }
    }
}
