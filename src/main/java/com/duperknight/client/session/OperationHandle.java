package com.duperknight.client.session;

import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.DMLSConfig;
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

    /**
     * Returns whether an automated command may be sent without exceeding the
     * shared non-admin spam budget. Dry runs never wait because they do not
     * produce an outbound packet.
     */
    public boolean canDispatchAutomatedCommand() {
        return isActive() && (descriptor.dryRunCaptured()
                || OutboundSpamSafety.canDispatch(DMLSConfig.staffRank() == StaffRank.ADMIN));
    }

    public int ticksUntilAutomatedCommandSafe() {
        if (!isActive() || descriptor.dryRunCaptured()) return 0;
        return OutboundSpamSafety.ticksUntilSafe(DMLSConfig.staffRank() == StaffRank.ADMIN);
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
