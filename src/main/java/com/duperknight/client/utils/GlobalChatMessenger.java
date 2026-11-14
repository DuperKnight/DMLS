package com.duperknight.client.utils;

import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import net.minecraft.client.Minecraft;

/** Sends module chat directly to global chat without changing the player's active channel. */
public final class GlobalChatMessenger {
    private static final String GLOBAL_COMMAND = "g";

    private GlobalChatMessenger() {
    }

    public static CommandDispatch dispatch(
            Minecraft client,
            String message,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection
    ) {
        return dispatch(client, message, dryRunCaptured, expectedConnection,
                DryRunFeedback.COMMAND_PREVIEW);
    }

    static CommandDispatch dispatch(
            Minecraft client,
            String message,
            boolean dryRunCaptured,
            ConnectionSnapshot expectedConnection,
            DryRunFeedback dryRunFeedback
    ) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty() || clean.indexOf('§') >= 0
                || clean.chars().anyMatch(Character::isISOControl)) {
            return CommandDispatch.BLOCKED;
        }
        return ClientUtils.dispatchCommand(client, commandFor(clean),
                dryRunCaptured, expectedConnection, dryRunFeedback);
    }

    static String commandFor(String message) {
        return GLOBAL_COMMAND + " " + message.trim();
    }
}
