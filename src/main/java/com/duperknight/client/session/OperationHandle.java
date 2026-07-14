package com.duperknight.client.session;

import net.minecraft.client.MinecraftClient;

import java.util.Objects;

/** Capability passed to an operation for dispatch, completion, and owner-scoped cancellation. */
public final class OperationHandle {
    private final OperationCoordinator coordinator;
    private final long sequence;
    private final OperationDescriptor descriptor;

    OperationHandle(OperationCoordinator coordinator, long sequence, OperationDescriptor descriptor) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.sequence = sequence;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public OperationDescriptor descriptor() {
        return descriptor;
    }

    public boolean isActive() {
        return coordinator.isActive(this);
    }

    public CommandDispatch dispatchCommand(MinecraftClient client, String command) {
        return coordinator.dispatchCommand(this, client, command);
    }

    public CommandDispatch dispatchChatMessage(MinecraftClient client, String message) {
        return coordinator.dispatchChatMessage(this, client, message);
    }

    public boolean complete() {
        return coordinator.complete(this);
    }

    public OperationCancelResult cancel(MinecraftClient client) {
        return coordinator.cancel(this, client, OperationCancelReason.MODULE_REQUESTED);
    }

    public OperationCancelResult cancel(MinecraftClient client, OperationCancelReason reason) {
        return coordinator.cancel(this, client, Objects.requireNonNull(reason, "reason"));
    }

    long sequence() {
        return sequence;
    }
}
