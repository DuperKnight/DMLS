package com.duperknight.client.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Validates, formats, and selects persisted new-player welcome messages. */
public final class GreeterMessages {
    public static final String PLAYER_VARIABLE = "{player}";
    public static final String DEFAULT_MESSAGE =
            "Welcome to Stoneworks, {player}! Enjoy your stay, and feel free to ask if you have any questions :)";
    public static final int MAX_TEMPLATE_LENGTH = 256;
    public static final int MAX_OUTPUT_LENGTH = 256;

    private static final String LONGEST_PLAYER_NAME = "1234567890123456";

    private GreeterMessages() {
    }

    /**
     * Trims and validates user-entered templates. Blank rows are omitted, so an empty result
     * intentionally means that the built-in default should be used.
     */
    public static Optional<List<String>> normalizeTemplates(List<String> templates) {
        if (templates == null) {
            return Optional.empty();
        }

        List<String> normalized = new ArrayList<>(templates.size());
        for (String template : templates) {
            String clean = template == null ? "" : template.strip();
            if (clean.isEmpty()) {
                continue;
            }
            if (!isValidTemplate(clean)) {
                return Optional.empty();
            }
            normalized.add(clean);
        }
        return Optional.of(List.copyOf(normalized));
    }

    /** Selects one valid custom template at random, or the built-in default when none are valid. */
    public static String choose(List<String> templates, String playerName, RandomGenerator random) {
        List<String> validTemplates = validTemplates(templates);
        String selected = validTemplates.isEmpty()
                ? DEFAULT_MESSAGE
                : validTemplates.get(random.nextInt(validTemplates.size()));
        return format(selected, playerName);
    }

    /** Replaces every currently supported variable in a selected template. */
    public static String format(String template, String playerName) {
        return template.replace(PLAYER_VARIABLE, playerName);
    }

    private static List<String> validTemplates(List<String> templates) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<String> valid = new ArrayList<>(templates.size());
        for (String template : templates) {
            String clean = template == null ? "" : template.strip();
            if (!clean.isEmpty() && isValidTemplate(clean)) {
                valid.add(clean);
            }
        }
        return valid;
    }

    private static boolean isValidTemplate(String template) {
        if (template.length() > MAX_TEMPLATE_LENGTH
                || template.indexOf('§') >= 0
                || template.codePoints().anyMatch(Character::isISOControl)
                || format(template, LONGEST_PLAYER_NAME).length() > MAX_OUTPUT_LENGTH) {
            return false;
        }

        String withoutSupportedVariables = template.replace(PLAYER_VARIABLE, "");
        return withoutSupportedVariables.indexOf('{') < 0
                && withoutSupportedVariables.indexOf('}') < 0;
    }
}
