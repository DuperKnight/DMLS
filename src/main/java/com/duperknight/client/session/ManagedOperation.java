package com.duperknight.client.session;

import com.duperknight.client.message.ServerMessage;
import net.minecraft.client.MinecraftClient;

/**
 * One response-tracked workflow managed by {@link OperationCoordinator}.
 * Callbacks run on the Minecraft client thread.
 */
public interface ManagedOperation {
    default void onStarted(OperationHandle handle, MinecraftClient client) {
    }

    default void onTick(OperationHandle handle, MinecraftClient client) {
    }

    default void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
    }

    default void onCancelled(
            OperationHandle handle,
            MinecraftClient client,
            OperationCancelReason reason
    ) {
    }
}
