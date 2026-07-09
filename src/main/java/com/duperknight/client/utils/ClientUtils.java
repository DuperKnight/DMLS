package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;

public final class ClientUtils {
    private ClientUtils() {
    }

    public static boolean isNotConnected(MinecraftClient client) {
        return client == null
                || client.player == null
                || client.world == null
                || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen();
    }

    public static void sendCommand(MinecraftClient client, String command) {
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand(command);
        }
    }
}
