package com.duperknight.client.rulebook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Search and ban-log metadata derived from one rendered rule box. */
public record RulebookRule(String id, String section, String title, String punishment,
                           int color, int blockIndex, String searchableText) {
    public RulebookRule {
        id = required(id, "id");
        section = required(section, "section");
        title = required(title, "title");
        punishment = Objects.requireNonNullElse(punishment, "").trim();
        searchableText = Objects.requireNonNullElse(searchableText, "").trim();
        if (blockIndex < 0) throw new IllegalArgumentException("blockIndex must be non-negative");
    }

    public String label() {
        return id + " " + title;
    }

    public String reason() {
        int sentenceEnd = -1;
        for (int index = 0; index < title.length(); index++) {
            if (title.charAt(index) == '.' && (index + 1 == title.length() || Character.isWhitespace(title.charAt(index + 1)))) {
                sentenceEnd = index;
                break;
            }
        }
        String firstSentence = sentenceEnd >= 0 ? title.substring(0, sentenceEnd + 1) : title;
        return id + " " + firstSentence.trim();
    }

    public boolean punishable() {
        return !punishment.isBlank();
    }

    public List<String> punishmentChoices() {
        String firstFooter = punishment.lines().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
        int sentenceEnd = firstFooter.indexOf(". ");
        if (sentenceEnd >= 0) firstFooter = firstFooter.substring(0, sentenceEnd + 1);
        firstFooter = firstFooter.replaceFirst("\\.$", "");
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        for (String choice : firstFooter.split("\\s*,\\s*")) {
            String normalized = choice.trim().replaceFirst("(?i)^then\\s+", "");
            if (!normalized.isBlank()) choices.add(normalized);
        }
        return List.copyOf(new ArrayList<>(choices));
    }

    public boolean matches(String needle) {
        String normalized = Objects.requireNonNullElse(needle, "").toLowerCase(Locale.ROOT);
        return id.toLowerCase(Locale.ROOT).contains(normalized)
                || section.toLowerCase(Locale.ROOT).contains(normalized)
                || title.toLowerCase(Locale.ROOT).contains(normalized)
                || punishment.toLowerCase(Locale.ROOT).contains(normalized)
                || searchableText.toLowerCase(Locale.ROOT).contains(normalized);
    }

    private static String required(String value, String name) {
        String clean = Objects.requireNonNullElse(value, "").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("missing " + name);
        return clean;
    }
}
