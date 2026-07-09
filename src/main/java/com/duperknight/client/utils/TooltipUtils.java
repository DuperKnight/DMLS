package com.duperknight.client.utils;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for interacting with the Minecraft tooltips.
 */
public final class TooltipUtils {
    private TooltipUtils() {
    }

    /**
     * Converts a Text object to a TooltipLine.
     *
     * @param text the Text object to convert
     * @return the resulting TooltipLine
     */
    public static TooltipLine toTooltipLine(Text text) {
        List<TooltipSegment> segments = new ArrayList<>();
        text.visit((style, value) -> {
            String cleaned = ChatUtils.cleanSegment(value);
            if (!cleaned.isEmpty()) {
                segments.add(new TooltipSegment(cleaned, style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        StringBuilder plainText = new StringBuilder();
        for (TooltipSegment segment : segments) {
            plainText.append(segment.text());
        }

        return new TooltipLine(plainText.toString().trim(), segments);
    }

    /**
     * Strips the list marker from the beginning of a line if it exists.
     *
     * @param line the line to strip the marker from
     * @return the stripped line
     */
    public static String stripListMarker(String line) {
        String stripped = line.trim();
        if (stripped.startsWith("-")) {
            stripped = stripped.substring(1).trim();
        }
        return stripped;
    }

    /**
     * Checks if a line is a footer line in a tooltip.
     *
     * @param line the line to check
     * @return true if the line is a footer line, false otherwise
     */
    public static boolean isTooltipFooter(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("minecraft:") || lower.contains(" component");
    }

    /**
     * Finds the last match of a pattern in a given text.
     *
     * @param text the text to search in
     * @param pattern the pattern to match
     * @return the last match, or an empty Optional if no match is found
     */
    public static Optional<String> lastMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return Optional.ofNullable(lastMatch);
    }

    /**
     * Returns a string representing the style prefix for a given style.
     * This is used to format the text in the tooltip.
     *
     * @param style the style to generate the prefix for
     * @return the style prefix string
     */
    public static String stylePrefix(Style style) {
        StringBuilder prefix = new StringBuilder();
        if (style.getColor() != null) {
            for (Formatting formatting : Formatting.values()) {
                if (formatting.isColor() && style.getColor().equals(TextColor.fromFormatting(formatting))) {
                    prefix.append('§').append(formatting.getCode());
                    break;
                }
            }
        }
        if (style.isBold()) {
            prefix.append("§l");
        }
        if (style.isItalic()) {
            prefix.append("§o");
        }
        if (style.isUnderlined()) {
            prefix.append("§n");
        }
        if (style.isStrikethrough()) {
            prefix.append("§m");
        }
        if (style.isObfuscated()) {
            prefix.append("§k");
        }
        return prefix.toString();
    }

    /**
     * Represents a line in a tooltip.
     *
     * @param text the text of the tooltip line
     * @param segments the segments of the tooltip line
     */
    public record TooltipLine(String text, List<TooltipSegment> segments) {
        public Optional<String> grayUsername(Pattern usernamePattern) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                TooltipSegment segment = segments.get(i);
                if (!segment.isGray()) {
                    continue;
                }

                Optional<String> username = lastMatch(segment.text(), usernamePattern);
                if (username.isPresent()) {
                    return username;
                }
            }

            return Optional.empty();
        }

        public Optional<String> formattedRoleBefore(String playerName) {
            int playerNameIndex = text.lastIndexOf(playerName);
            if (playerNameIndex < 0) {
                return Optional.empty();
            }

            String role = stripListMarker(text.substring(0, playerNameIndex)).trim();
            if (role.isEmpty()) {
                return Optional.empty();
            }

            Style roleStyle = Style.EMPTY;
            for (TooltipSegment segment : segments) {
                if (segment.isGray() && segment.text().contains(playerName)) {
                    break;
                }
                if (!segment.isGray() && !segment.text().isBlank()) {
                    roleStyle = segment.style();
                }
            }

            return Optional.of(stylePrefix(roleStyle) + role);
        }
    }

    /**
     * Represents a segment of text in a tooltip line.
     *
     * @param text the text of the tooltip segment
     * @param style the style of the tooltip segment
     */
    public record TooltipSegment(String text, Style style) {
        private static final TextColor GRAY = TextColor.fromFormatting(Formatting.GRAY);

        public boolean isGray() {
            return java.util.Objects.equals(style.getColor(), GRAY);
        }
    }
}
