package com.duperknight.client.war;

import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
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

/** Parses the Lands item at column 4, row 2 in `/n info`. */
public final class NationMenuParser {
    private static final Pattern COUNT = Pattern.compile("\\((\\d+)\\)");

    private NationMenuParser() {
    }

    public static Result parse(List<TooltipLine> tooltip) {
        int declaredCount = -1;
        boolean inLands = false;
        boolean truncated = false;
        String capital = "";
        List<String> lands = new ArrayList<>();

        for (TooltipLine line : List.copyOf(tooltip)) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            if (!inLands) {
                if (!stripped.toLowerCase(Locale.ROOT).startsWith("lands")) continue;
                inLands = true;
                Matcher count = COUNT.matcher(stripped);
                if (count.find()) declaredCount = Integer.parseInt(count.group(1));
                continue;
            }
            if (TooltipUtils.isTooltipFooter(stripped)) continue;
            if (stripped.equals("...")) {
                truncated = true;
                continue;
            }
            if (stripped.regionMatches(true, 0, "Capital ", 0, "Capital ".length())) {
                capital = stripped.substring("Capital ".length()).trim();
                if (!capital.isEmpty()) lands.add(capital);
            } else if (!stripped.isBlank()) {
                lands.add(stripped);
            }
        }

        if (!inLands || capital.isEmpty()) return Result.malformed();
        int actualCount = declaredCount >= 0 ? declaredCount : lands.size();
        if (actualCount < lands.size() || actualCount < 1) return Result.malformed();
        String finalCapital = capital;
        String alternate = lands.stream().filter(land -> !land.equalsIgnoreCase(finalCapital))
                .findFirst().orElse("");
        boolean soleCapital = actualCount == 1 && !truncated && alternate.isEmpty();
        return new Result(true, capital, alternate, actualCount, truncated, soleCapital);
    }

    /** Extracts a styled nation-name substring and emits Lands-compatible ampersand color codes. */
    public static String coloredNationName(Text title, String plainNationName) {
        if (title == null || plainNationName == null || plainNationName.isBlank()) return "";
        String target = plainNationName.trim();
        String whole = title.getString();
        int start = indexOfIgnoreCase(whole, target);
        if (start < 0) start = indexOfIgnoreCase(whole, target.replace('_', ' '));
        if (start < 0) return target;
        int end = Math.min(whole.length(), start + target.length());

        StringBuilder result = new StringBuilder();
        int[] position = {0};
        String[] activeColor = {""};
        int matchStart = start;
        int matchEnd = end;
        title.visit((style, value) -> {
            int segmentStart = position[0];
            int segmentEnd = segmentStart + value.length();
            int from = Math.max(matchStart, segmentStart);
            int to = Math.min(matchEnd, segmentEnd);
            if (from < to) {
                String code = colorCode(style);
                if (!code.equals(activeColor[0])) {
                    if (code.isEmpty() && !activeColor[0].isEmpty()) result.append("&r");
                    else if (!code.isEmpty()) result.append(code);
                    activeColor[0] = code;
                }
                result.append(value, from - segmentStart, to - segmentStart);
            }
            position[0] = segmentEnd;
            return Optional.empty();
        }, Style.EMPTY);
        return result.isEmpty() ? target : result.toString();
    }

    private static int indexOfIgnoreCase(String whole, String target) {
        return whole.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
    }

    private static String colorCode(Style style) {
        TextColor color = style.getColor();
        if (color == null) return "";
        for (Formatting formatting : Formatting.values()) {
            if (formatting.isColor() && color.equals(TextColor.fromFormatting(formatting))) {
                return "&" + formatting.getCode();
            }
        }
        return "&#%06X".formatted(color.getRgb() & 0xFFFFFF);
    }

    public record Result(boolean parsed, String capital, String alternateCapital,
                         int declaredCount, boolean truncated, boolean soleCapital) {
        private static Result malformed() {
            return new Result(false, "", "", 0, false, false);
        }
    }
}
