package com.duperknight.client.utils;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/**
 * Minecraft 26.x removed {@code ChatFormatting#isColor}, {@code getColor} and {@code getChar}.
 * These helpers recreate them for the 16 legacy colour codes.
 */
public final class LegacyColors {
    private static final List<ChatFormatting> COLORS = List.of(
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE);
    private static final String CODES = "0123456789abcdef";

    private LegacyColors() {
    }

    /** True for the 16 colour formats (not bold, italic, reset and so on). */
    public static boolean isColor(ChatFormatting formatting) {
        return COLORS.contains(formatting);
    }

    /** The legacy section-sign code character for a colour; 'r' (reset) for non-colours. */
    public static char code(ChatFormatting formatting) {
        int index = COLORS.indexOf(formatting);
        return index < 0 ? 'r' : CODES.charAt(index);
    }

    /** The RGB value of a colour format, or null for non-colours. */
    public static Integer rgb(ChatFormatting formatting) {
        TextColor color = TextColor.fromLegacyFormat(formatting);
        return color == null ? null : color.getValue();
    }
}