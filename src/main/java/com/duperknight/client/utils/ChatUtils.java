package com.duperknight.client.utils;


import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

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
    public static void sendClientMessage(Minecraft client, String message) {
        sendClientMessage(client, Component.literal(message));
    }

    /**
     * Sends a text component to the client's chat.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendClientMessage(Minecraft client, Component message) {
        if (client != null && client.gui != null) {
            // Add directly to the HUD. ClientPlayerEntity#sendMessage fires GAME receive
            // callbacks and made local DMLS notifications look like server messages.
            client.gui.hud.getChat().addClientSystemMessage(message);
        }
    }

    /** Sends a translated message after a module's formatted chat prefix. */
    public static void sendTranslatedMessage(Minecraft client, String prefix, String translationKey, Object... args) {
        sendClientMessage(client, Component.literal(prefix).append(translated(translationKey, args)));
    }

    /** Gray DMLS prose with non-Text placeholders highlighted in gold. */
    public static Component translated(String translationKey, Object... args) {
        Object[] styledArgs = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            styledArgs[index] = args[index] instanceof Component text
                    ? text
                    : Component.literal(String.valueOf(args[index])).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable(translationKey, styledArgs).withStyle(ChatFormatting.GRAY);
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
    public static String separatorForChatWidth(Minecraft client, String linePrefix) {
        int chatWidth = ChatComponent.getWidth(client.options.chatWidth().get());
        int prefixWidth = client.font.width(stripFormatting(linePrefix));
        int dashWidth = Math.max(1, client.font.width("-"));
        int dashCount = Math.max(3, (chatWidth - prefixWidth) / dashWidth);
        return "-".repeat(dashCount);
    }
}
