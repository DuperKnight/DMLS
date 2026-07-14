package com.duperknight.client.parser;

import java.util.Locale;
import java.util.regex.Pattern;

/** Matching is intentionally narrow until anonymized Stoneworks response fixtures are available. */
public final class CoreProtectResponseParser {
    private static final Pattern REJECTED = Pattern.compile(
            "^(?:\\[coreprotect]\\s*)?(?:error|rollback failed|restore failed|no permission)(?:[:.!].*)?$",
            Pattern.CASE_INSENSITIVE);

    private CoreProtectResponseParser() {
    }

    public static Result parse(String cleanText) {
        return parse("rollback", cleanText);
    }

    /** Parses only completion for the expected destructive action; unrelated success text never advances it. */
    public static Result parse(String expectedAction, String cleanText) {
        String action = expectedAction == null ? "" : expectedAction.trim().toLowerCase(Locale.ROOT);
        String value = cleanText == null ? "" : cleanText.trim().toLowerCase(Locale.ROOT);
        if (REJECTED.matcher(value).matches()) return Result.REJECTED;
        if ((action.equals("rollback") || action.equals("restore"))
                && value.matches("^(?:\\[coreprotect]\\s*)?" + Pattern.quote(action) + " complete[.!]?$")) {
            return Result.CONFIRMED;
        }
        return Result.UNRELATED;
    }

    public enum Result { CONFIRMED, REJECTED, UNRELATED }
}
