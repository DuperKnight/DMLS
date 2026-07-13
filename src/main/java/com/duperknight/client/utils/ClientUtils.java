package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import net.minecraft.client.MinecraftClient;

/**
 * Utility methods for interacting with the Minecraft client.
 */
public final class ClientUtils {
    private static final String DRY_RUN_PREFIX = "§8[§6DMLS - DryRun§8] §7";

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
    public static boolean sendCommand(MinecraftClient client, String command) {
        return dispatchCommand(client, command).accepted();
    }

    /** Dispatches a one-shot command using the dry-run state observed at this call. */
    public static CommandDispatch dispatchCommand(MinecraftClient client, String command) {
        return dispatchCommand(client, command, DMLSConfig.dryRun(), ConnectionSnapshot.capture(client));
    }

    /**
     * Dispatches using operation-start state. The captured flag never consults the mutable global
     * dry-run setting, and the expected connection protects against same-host reconnects.
     */
    public static CommandDispatch dispatchCommand(
            MinecraftClient client,
            String command,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection
    ) {
        if (command == null || command.isBlank() || expectedConnection == null
                || !expectedConnection.matches(client)) {
            return CommandDispatch.BLOCKED;
        }
        if (dryRunCaptured) {
            ChatUtils.sendTranslatedMessage(client, DRY_RUN_PREFIX,
                    "dmls.chat.dry_run.would_run", "/" + command);
            return CommandDispatch.SIMULATED;
        }
        if (!ServerGuard.check(client).allowed() || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen()) {
            return CommandDispatch.BLOCKED;
        }
        try {
            client.getNetworkHandler().sendChatCommand(command);
            return CommandDispatch.SENT;
        } catch (RuntimeException exception) {
            DMLS.LOGGER.warn("Failed to dispatch a guarded command", exception);
            return CommandDispatch.BLOCKED;
        }
    }

    /**
     * Sends a chat message to the server as the player.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendChatMessage(MinecraftClient client, String message) {
        dispatchChatMessage(client, message);
    }

    /** Dispatches a one-shot chat message using the dry-run state observed at this call. */
    public static CommandDispatch dispatchChatMessage(MinecraftClient client, String message) {
        return dispatchChatMessage(client, message, DMLSConfig.dryRun(), ConnectionSnapshot.capture(client));
    }

    /** Guarded chat-message equivalent of {@link #dispatchCommand}. */
    public static CommandDispatch dispatchChatMessage(
            MinecraftClient client,
            String message,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection
    ) {
        if (message == null || message.isBlank() || expectedConnection == null
                || !expectedConnection.matches(client)) {
            return CommandDispatch.BLOCKED;
        }
        if (dryRunCaptured) {
            ChatUtils.sendTranslatedMessage(client, DRY_RUN_PREFIX,
                    "dmls.chat.dry_run.would_say", message);
            return CommandDispatch.SIMULATED;
        }
        if (!ServerGuard.check(client).allowed() || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen()) {
            return CommandDispatch.BLOCKED;
        }
        try {
            client.getNetworkHandler().sendChatMessage(message);
            return CommandDispatch.SENT;
        } catch (RuntimeException exception) {
            DMLS.LOGGER.warn("Failed to dispatch a guarded chat message", exception);
            return CommandDispatch.BLOCKED;
        }
    }
}
