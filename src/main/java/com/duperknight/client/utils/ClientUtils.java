package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;

/**
 * Utility methods for interacting with the Minecraft client.
 */
public final class ClientUtils {
    private ClientUtils() {
    }

    /**
     * Checks if the client is not connected to a server.
     *
     * @param client the Minecraft client
     * @return true if the client is not connected, false otherwise
     */
    public static boolean isNotConnected(MinecraftClient client) {
        return client == null
                || client.player == null
                || client.world == null
                || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen();
    }

    /**
     * Sends a chat command to the client.
     *
     * @param client the Minecraft client
     * @param command the command to send
     */
    public static void sendCommand(MinecraftClient client, String command) {
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand(command);
        }
    }
}
