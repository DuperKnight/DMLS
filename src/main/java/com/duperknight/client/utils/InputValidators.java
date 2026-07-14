package com.duperknight.client.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public final class InputValidators {
    public static final int MAX_PREFIX_ID_LENGTH = 32;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern PREFIX_ID = Pattern.compile("[A-Za-z0-9_-]{1," + MAX_PREFIX_ID_LENGTH + "}");

    private InputValidators() {
    }

    public static boolean isUsername(String value) {
        return value != null && USERNAME.matcher(value).matches();
    }

    public static boolean isServerIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    public static boolean isPrefixId(String value) {
        return value != null && PREFIX_ID.matcher(value).matches();
    }

    /** Allows spaces but rejects blank, oversized, or control-bearing command arguments. */
    public static boolean isSafeCommandArgument(String value, int maxLength) {
        return value != null && maxLength > 0 && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    public static List<String> uniqueUsernames(String input, List<String> rejected) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (input == null) return List.of();
        for (String value : input.trim().split("[,\\s]+")) {
            if (value.isEmpty()) continue;
            if (!isUsername(value)) rejected.add(value);
            else unique.putIfAbsent(value.toLowerCase(java.util.Locale.ROOT), value);
        }
        return new ArrayList<>(unique.values());
    }

    public static boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
