package com.duperknight.client.session;

import com.duperknight.client.message.ServerMessage;
import net.minecraft.client.Minecraft;

/**
 * One response-tracked workflow managed by {@link OperationCoordinator}.
 * Callbacks run on the Minecraft client thread.
 */
public interface ManagedOperation {
    default void onStarted(OperationHandle handle, Minecraft client) {
    }

    default void onTick(OperationHandle handle, Minecraft client) {
    }

    default void onServerMessage(OperationHandle handle, Minecraft client, ServerMessage message) {
    }

    default void onCancelled(
            OperationHandle handle,
            Minecraft client,
            OperationCancelReason reason
    ) {
    }
}
