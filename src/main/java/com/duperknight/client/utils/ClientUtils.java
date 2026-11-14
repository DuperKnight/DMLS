package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

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
    public static boolean isNotConnected(Minecraft client) {
        return client == null
                || client.player == null
                || client.level == null
                || client.getConnection() == null
                || !client.getConnection().isAcceptingMessages();
    }

    /**
     * Returns a snapshot of the online player names advertised to this client.
     * This is the player-name source used by vanilla chat autocomplete.
     *
     * @param client the Minecraft client
     * @return the known online player names, or an empty list when disconnected
     */
    public static List<String> getOnlinePlayerNames(Minecraft client) {
        ClientPacketListener networkHandler = client == null ? null : client.getConnection();
        if (networkHandler == null || !networkHandler.isAcceptingMessages()) return List.of();

        return networkHandler.getOnlinePlayers().stream()
                .map(entry -> entry.getProfile().name())
                .toList();
    }

    /**
     * Sends a chat command to the client.
     *
     * @param client the Minecraft client
     * @param command the command to send
     */
    public static boolean sendCommand(Minecraft client, String command) {
        return dispatchCommand(client, command).accepted();
    }

    /** Dispatches a one-shot command using the dry-run state observed at this call. */
    public static CommandDispatch dispatchCommand(Minecraft client, String command) {
        return dispatchCommand(client, command, DMLSConfig.dryRun(), ConnectionSnapshot.capture(client),
                DryRunFeedback.COMMAND_PREVIEW);
    }

    /**
     * Dispatches using operation-start state. The captured flag never consults the mutable global
     * dry-run setting, and the expected connection protects against same-host reconnects.
     */
    public static CommandDispatch dispatchCommand(
            Minecraft client,
            String command,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection
    ) {
        return dispatchCommand(client, command, dryRunCaptured, expectedConnection,
                DryRunFeedback.COMMAND_PREVIEW);
    }

    /** Dispatches with an explicit dry-run reporting policy. */
    public static CommandDispatch dispatchCommand(
            Minecraft client,
            String command,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection,
            DryRunFeedback dryRunFeedback
    ) {
        if (command == null || command.isBlank() || expectedConnection == null
                || dryRunFeedback == null || !expectedConnection.matches(client)) {
            return CommandDispatch.BLOCKED;
        }
        if (dryRunCaptured) {
            if (dryRunFeedback == DryRunFeedback.COMMAND_PREVIEW) {
                ChatUtils.sendTranslatedMessage(client, DRY_RUN_PREFIX,
                        "dmls.chat.dry_run.would_run", "/" + command);
            }
            return CommandDispatch.SIMULATED;
        }
        if (!ServerGuard.check(client).allowed() || client.getConnection() == null
                || !client.getConnection().isAcceptingMessages()) {
            return CommandDispatch.BLOCKED;
        }
        try {
            client.getConnection().sendCommand(command);
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
    public static void sendChatMessage(Minecraft client, String message) {
        dispatchChatMessage(client, message);
    }

    /** Dispatches a one-shot chat message using the dry-run state observed at this call. */
    public static CommandDispatch dispatchChatMessage(Minecraft client, String message) {
        return dispatchChatMessage(client, message, DMLSConfig.dryRun(), ConnectionSnapshot.capture(client));
    }

    /** Guarded chat-message equivalent of {@link #dispatchCommand}. */
    public static CommandDispatch dispatchChatMessage(
            Minecraft client,
            String message,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection
    ) {
        return dispatchChatMessage(client, message, dryRunCaptured, expectedConnection,
                DryRunFeedback.COMMAND_PREVIEW);
    }

    /** Guarded chat-message dispatch with an explicit dry-run reporting policy. */
    public static CommandDispatch dispatchChatMessage(
            Minecraft client,
            String message,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection,
            DryRunFeedback dryRunFeedback
    ) {
        return GlobalChatMessenger.dispatch(client, message, dryRunCaptured, expectedConnection, dryRunFeedback);
    }

}
