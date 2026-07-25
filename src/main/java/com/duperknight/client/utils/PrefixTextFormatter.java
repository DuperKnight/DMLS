package com.duperknight.client.utils;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/** Parses server prefix text for the in-game preview without changing the submitted value. */
public final class PrefixTextFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private PrefixTextFormatter() {
    }

    public static ParseResult parse(String input) {
        if (input == null || input.isEmpty()) {
            return ParseResult.error("Enter prefix text to preview it.");
        }

        try {
            String miniMessage = normalizeLegacyFormatting(input);
            Component component = MINI_MESSAGE.deserialize(miniMessage);
            String json = GsonComponentSerializer.gson().serialize(component);
            Text preview = TextCodecs.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .getOrThrow(IllegalArgumentException::new);
            return ParseResult.success(preview);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return ParseResult.error(message == null || message.isBlank()
                    ? "The prefix formatting is invalid."
                    : "Invalid prefix formatting: " + message);
        }
    }

    /** Serializes the same MiniMessage/legacy input used by the preview into vanilla text JSON. */
    public static JsonResult serializeJson(String input) {
        if (input == null || input.isEmpty()) {
            return JsonResult.error("Enter text to format it.");
        }
        try {
            Component component = MINI_MESSAGE.deserialize(normalizeLegacyFormatting(input));
            return JsonResult.success(GsonComponentSerializer.gson().serialize(component));
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return JsonResult.error(message == null || message.isBlank()
                    ? "The text formatting is invalid."
                    : "Invalid text formatting: " + message);
        }
    }

    /** Returns the visible, formatting-free MiniMessage text for audit logs. */
    public static PlainResult plainText(String input) {
        if (input == null || input.isEmpty()) return PlainResult.error("Enter text to format it.");
        try {
            Component component = MINI_MESSAGE.deserialize(normalizeLegacyFormatting(input));
            StringBuilder plain = new StringBuilder();
            appendPlain(component, plain);
            return PlainResult.success(plain.toString());
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return PlainResult.error(message == null || message.isBlank()
                    ? "The text formatting is invalid."
                    : "Invalid text formatting: " + message);
        }
    }

    private static void appendPlain(Component component, StringBuilder target) {
        if (component instanceof TextComponent text) target.append(text.content());
        component.children().forEach(child -> appendPlain(child, target));
    }

    /** Converts legacy formatting into MiniMessage tags while leaving the submitted text untouched. */
    static String normalizeLegacyFormatting(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char marker = input.charAt(index);
            if (marker != '&' && marker != '\u00A7') {
                result.append(marker);
                continue;
            }

            if (index + 1 >= input.length()) {
                result.append(marker);
                continue;
            }

            char code = input.charAt(index + 1);
            if (code == '#') {
                if (index + 8 > input.length()) {
                    throw new IllegalArgumentException("hex colors must use &#RRGGBB");
                }
                String hex = input.substring(index + 2, index + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    result.append("<#").append(hex).append('>');
                    index += 7;
                    continue;
                }
                throw new IllegalArgumentException("hex colors must use &#RRGGBB");
            }

            String tag = legacyTag(code);
            if (tag != null) {
                result.append('<').append(tag).append('>');
                index++;
                continue;
            }

            result.append(marker);
        }
        return result.toString();
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }

    public record ParseResult(Text preview, String error) {
        public static ParseResult success(Text preview) {
            return new ParseResult(preview, "");
        }

        public static ParseResult error(String error) {
            return new ParseResult(Text.empty(), error);
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }

    public record JsonResult(String json, String error) {
        public static JsonResult success(String json) {
            return new JsonResult(json, "");
        }

        public static JsonResult error(String error) {
            return new JsonResult("", error);
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }

    public record PlainResult(String text, String error) {
        public static PlainResult success(String text) {
            return new PlainResult(text, "");
        }

        public static PlainResult error(String error) {
            return new PlainResult("", error);
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }
}
