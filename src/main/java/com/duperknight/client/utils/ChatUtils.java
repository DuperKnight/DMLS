package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

import java.util.regex.Pattern;

public final class ChatUtils {
    private static final Pattern FORMATTING_CODE = Pattern.compile("§.");

    private ChatUtils() {
    }

    public static void sendClientMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    public static String cleanLine(String line) {
        return stripFormatting(line).trim();
    }

    public static String cleanSegment(String line) {
        return stripFormatting(line);
    }

    public static String stripFormatting(String line) {
        return FORMATTING_CODE.matcher(line).replaceAll("");
    }

    public static String separatorForChatWidth(MinecraftClient client, String linePrefix) {
        int chatWidth = ChatHud.getWidth(client.options.getChatWidth().getValue());
        int prefixWidth = client.textRenderer.getWidth(stripFormatting(linePrefix));
        int dashWidth = Math.max(1, client.textRenderer.getWidth("-"));
        int dashCount = Math.max(3, (chatWidth - prefixWidth) / dashWidth);
        return "-".repeat(dashCount);
    }
}
