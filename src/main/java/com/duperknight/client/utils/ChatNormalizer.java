package com.duperknight.client.utils;

import java.text.Normalizer;
import java.util.Locale;

public final class ChatNormalizer {
    private ChatNormalizer() {
    }

    /**
     * Normalizes text for matching against the alert wordlist.
     * Lowercases, strips accents, maps leetspeak characters and removes everything that is not a letter.
     *
     * @param input the text to normalize
     * @return the normalized text
     */
    public static String normalize(String input) {
        String decomposed = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");

        StringBuilder result = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char mapped = mapLeet(decomposed.charAt(i));
            if (mapped >= 'a' && mapped <= 'z') {
                result.append(mapped);
            }
        }
        return result.toString();
    }

    /**
     * Collapses repeated letters to catch stretched out words.
     *
     * @param normalized the normalized text
     * @return the text without repeated letters
     */
    public static String collapseRepeats(String normalized) {
        StringBuilder result = new StringBuilder(normalized.length());
        char previous = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current != previous) {
                result.append(current);
            }
            previous = current;
        }
        return result.toString();
    }

    private static char mapLeet(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1', '!', '|' -> 'i';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '6', '9' -> 'g';
            case '7' -> 't';
            case '8' -> 'b';
            case '2' -> 'z';
            default -> c;
        };
    }
}
