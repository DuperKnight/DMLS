package com.duperknight.client.session;

import com.duperknight.DMLS;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Single global owner of response-tracked operation lifecycle and dispatch safety.
 */
public final class OperationCoordinator {
    private static final OperationCoordinator GLOBAL = new OperationCoordinator();

    private ActiveOperation active;
    private long nextSequence;
    private boolean registered;
    private final Function<MinecraftClient, ConnectionSnapshot> connectionProvider;

    public OperationCoordinator() {
        this(ConnectionSnapshot::capture);
    }

    OperationCoordinator(Function<MinecraftClient, ConnectionSnapshot> connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    public static OperationCoordinator global() {
        return GLOBAL;
    }

    /** Registers the global tick and message callbacks once. */
    public synchronized void register() {
        if (registered) return;
        registered = true;
        ServerMessageRouter.register();
        ServerMessageRouter.subscribe(EnumSet.of(
                MessageOrigin.PLAYER_CHAT,
                MessageOrigin.SERVER_SYSTEM,
                MessageOrigin.OVERLAY
        ), this::onServerMessage);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public OperationStartResult start(
            MinecraftClient client,
            String operationId,
            String displayName,
            ManagedOperation operation
    ) {
        if (operation == null || operationId == null || operationId.isBlank()
                || displayName == null || displayName.isBlank()) {
            return OperationStartResult.INVALID;
        }
        return start(client, OperationDescriptor.capture(client, operationId, displayName), operation);
    }

    public OperationStartResult start(
            MinecraftClient client,
            OperationDescriptor descriptor,
            ManagedOperation operation
    ) {
        if (descriptor == null || operation == null) return OperationStartResult.INVALID;

        synchronized (this) {
            if (active != null) return OperationStartResult.BUSY;
        }

        if (!descriptor.connectionStillMatches(connectionProvider.apply(client))) {
            return OperationStartResult.SERVER_BLOCKED;
        }
        if (!descriptor.dryRunCaptured() && (!descriptor.connection().connected()
                || !ServerGuard.isAllowed(descriptor.connection().serverAddress(), DMLSConfig.allowedServers()))) {
            return OperationStartResult.SERVER_BLOCKED;
        }

        ActiveOperation started;
        synchronized (this) {
            if (active != null) return OperationStartResult.BUSY;
            OperationHandle handle = new OperationHandle(this, ++nextSequence, descriptor);
            started = new ActiveOperation(handle, operation);
            active = started;
        }

        try {
            operation.onStarted(started.handle, client);
            return OperationStartResult.STARTED;
        } catch (RuntimeException exception) {
            DMLS.LOGGER.error("Managed operation '{}' failed to start", descriptor.operationId(), exception);
            cancel(started.handle, client, OperationCancelReason.INTERNAL_ERROR);
            return OperationStartResult.FAILED_TO_START;
        }
    }

    public OperationCancelResult cancelActive(MinecraftClient client) {
        ActiveOperation removed;
        synchronized (this) {
            if (active == null) return OperationCancelResult.NO_ACTIVE_OPERATION;
            removed = active;
            active = null;
        }
        notifyCancelled(removed, client, OperationCancelReason.USER_REQUESTED);
        return OperationCancelResult.CANCELLED;
    }

    /** Cancels only when the named module owns the active slot. */
    public OperationCancelResult cancel(String operationId, MinecraftClient client) {
        if (operationId == null) return OperationCancelResult.NOT_OWNER;
        ActiveOperation removed;
        synchronized (this) {
            if (active == null) return OperationCancelResult.NO_ACTIVE_OPERATION;
            if (!active.handle.descriptor().operationId().equals(operationId)) {
                return OperationCancelResult.NOT_OWNER;
            }
            removed = active;
            active = null;
        }
        notifyCancelled(removed, client, OperationCancelReason.MODULE_REQUESTED);
        return OperationCancelResult.CANCELLED;
    }

    public void tick(MinecraftClient client) {
        ActiveOperation current = activeSnapshot();
        if (current == null) return;
        if (!current.handle.descriptor().connectionStillMatches(connectionProvider.apply(client))) {
            cancel(current.handle, client, OperationCancelReason.CONNECTION_CHANGED);
            return;
        }

        try {
            current.operation.onTick(current.handle, client);
        } catch (RuntimeException exception) {
            DMLS.LOGGER.error("Managed operation '{}' failed during tick",
                    current.handle.descriptor().operationId(), exception);
            cancel(current.handle, client, OperationCancelReason.INTERNAL_ERROR);
        }
    }

    public synchronized Optional<OperationDescriptor> activeDescriptor() {
        return active == null ? Optional.empty() : Optional.of(active.handle.descriptor());
    }

    public synchronized boolean isBusy() {
        return active != null;
    }

    boolean isActive(OperationHandle handle) {
        synchronized (this) {
            return ownsActive(handle);
        }
    }

    CommandDispatch dispatchCommand(OperationHandle handle, MinecraftClient client, String command) {
        if (!isActive(handle)) return CommandDispatch.BLOCKED;
        OperationDescriptor descriptor = handle.descriptor();
        return ClientUtils.dispatchCommand(client, command,
                descriptor.dryRunCaptured(), descriptor.connection());
    }

    CommandDispatch dispatchChatMessage(OperationHandle handle, MinecraftClient client, String message) {
        if (!isActive(handle)) return CommandDispatch.BLOCKED;
        OperationDescriptor descriptor = handle.descriptor();
        return ClientUtils.dispatchChatMessage(client, message,
                descriptor.dryRunCaptured(), descriptor.connection());
    }

    boolean complete(OperationHandle handle) {
        synchronized (this) {
            if (!ownsActive(handle)) return false;
            active = null;
            return true;
        }
    }

    OperationCancelResult cancel(
            OperationHandle handle,
            MinecraftClient client,
            OperationCancelReason reason
    ) {
        ActiveOperation removed;
        synchronized (this) {
            if (active == null) return OperationCancelResult.NO_ACTIVE_OPERATION;
            if (!ownsActive(handle)) return OperationCancelResult.NOT_OWNER;
            removed = active;
            active = null;
        }
        notifyCancelled(removed, client, reason);
        return OperationCancelResult.CANCELLED;
    }

    private void onServerMessage(ServerMessage message) {
        ActiveOperation current = activeSnapshot();
        if (current == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!current.handle.descriptor().connectionStillMatches(connectionProvider.apply(client))) {
            cancel(current.handle, client, OperationCancelReason.CONNECTION_CHANGED);
            return;
        }
        try {
            current.operation.onServerMessage(current.handle, client, message);
        } catch (RuntimeException exception) {
            DMLS.LOGGER.error("Managed operation '{}' failed while parsing a server message",
                    current.handle.descriptor().operationId(), exception);
            cancel(current.handle, client, OperationCancelReason.INTERNAL_ERROR);
        }
    }

    private synchronized ActiveOperation activeSnapshot() {
        return active;
    }

    private boolean ownsActive(OperationHandle handle) {
        return handle != null && active != null
                && active.handle.sequence() == handle.sequence()
                && active.handle == handle;
    }

    private void notifyCancelled(
            ActiveOperation removed,
            MinecraftClient client,
            OperationCancelReason reason
    ) {
        Objects.requireNonNull(reason, "reason");
        try {
            removed.operation.onCancelled(removed.handle, client, reason);
        } catch (RuntimeException exception) {
            DMLS.LOGGER.error("Managed operation '{}' failed while cancelling",
                    removed.handle.descriptor().operationId(), exception);
        }
    }

    private record ActiveOperation(OperationHandle handle, ManagedOperation operation) {
    }
}
