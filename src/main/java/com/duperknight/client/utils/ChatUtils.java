package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

import java.util.regex.Pattern;

/**
 * Utility methods for interacting with the Minecraft chat.
 */
public final class ChatUtils {
    private static final Pattern FORMATTING_CODE = Pattern.compile("§.");

    private ChatUtils() {
    }

    /**
     * Sends a message to the client's chat.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendClientMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    /**
     * Cleans a line of text by stripping formatting codes and trimming whitespace.
     *
     * @param line the line to clean
     * @return the cleaned line
     */
    public static String cleanLine(String line) {
        return stripFormatting(line).trim();
    }

    /**
     * Cleans a segment of text by stripping formatting codes.
     *
     * @param line the line to clean
     * @return the cleaned line
     */
    public static String cleanSegment(String line) {
        return stripFormatting(line);
    }

    /**
     * Strips all formatting codes from a string.
     *
     * @param line the line to strip formatting from
     * @return the stripped line
     */
    public static String stripFormatting(String line) {
        return FORMATTING_CODE.matcher(line).replaceAll("");
    }

    /**
     * Generates a separator line for chat messages based on the client's chat width.
     *
     * @param client the Minecraft client
     * @param linePrefix the prefix for the line
     * @return the separator line
     */
    public static String separatorForChatWidth(MinecraftClient client, String linePrefix) {
        int chatWidth = ChatHud.getWidth(client.options.getChatWidth().getValue());
        int prefixWidth = client.textRenderer.getWidth(stripFormatting(linePrefix));
        int dashWidth = Math.max(1, client.textRenderer.getWidth("-"));
        int dashCount = Math.max(3, (chatWidth - prefixWidth) / dashWidth);
        return "-".repeat(dashCount);
    }
}
