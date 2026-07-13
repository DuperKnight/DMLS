package com.duperknight.client.session;

import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationCoordinatorTest {
    @AfterEach
    void resetDryRun() {
        DMLSConfig.setDryRun(false);
    }

    @Test
    void enforcesSingleOwnerAndModuleScopedCancellation() {
        AtomicReference<ConnectionSnapshot> connection = new AtomicReference<>(ConnectionSnapshot.disconnected());
        OperationCoordinator coordinator = new OperationCoordinator(ignored -> connection.get());
        RecordingOperation first = new RecordingOperation();
        OperationDescriptor descriptor = descriptor("xray", connection.get());

        assertEquals(OperationStartResult.STARTED, coordinator.start(null, descriptor, first));
        assertEquals(OperationStartResult.BUSY,
                coordinator.start(null, descriptor("promo", connection.get()), new RecordingOperation()));
        assertEquals(OperationCancelResult.NOT_OWNER, coordinator.cancel("promo", null));

        coordinator.tick(null);
        assertEquals(1, first.ticks);
        assertEquals(OperationCancelResult.CANCELLED, coordinator.cancel("xray", null));
        assertEquals(OperationCancelReason.MODULE_REQUESTED, first.cancelReason);
        assertFalse(coordinator.isBusy());
    }

    @Test
    void sameHostReconnectCancelsAndMakesOldHandleStale() {
        Object originalHandler = new Object();
        AtomicReference<ConnectionSnapshot> connection = new AtomicReference<>(
                ConnectionSnapshot.connected("play.stoneworks.gg", originalHandler));
        OperationCoordinator coordinator = new OperationCoordinator(ignored -> connection.get());
        RecordingOperation operation = new RecordingOperation();

        assertEquals(OperationStartResult.STARTED,
                coordinator.start(null, descriptor("scan", connection.get()), operation));
        OperationHandle oldHandle = operation.handle;

        connection.set(ConnectionSnapshot.connected("play.stoneworks.gg", new Object()));
        coordinator.tick(null);

        assertEquals(OperationCancelReason.CONNECTION_CHANGED, operation.cancelReason);
        assertFalse(coordinator.isBusy());
        assertFalse(oldHandle.complete());

        RecordingOperation replacement = new RecordingOperation();
        assertEquals(OperationStartResult.STARTED,
                coordinator.start(null, descriptor("scan", connection.get()), replacement));
        assertFalse(oldHandle.isActive());
        assertTrue(replacement.handle.isActive());
    }

    @Test
    void globalCancelUsesUserReasonAndStartFailureReleasesSlot() {
        OperationCoordinator coordinator = new OperationCoordinator(ignored -> ConnectionSnapshot.disconnected());
        RecordingOperation operation = new RecordingOperation();
        assertEquals(OperationStartResult.STARTED,
                coordinator.start(null, descriptor("prefix", ConnectionSnapshot.disconnected()), operation));
        assertEquals(OperationCancelResult.CANCELLED, coordinator.cancelActive(null));
        assertEquals(OperationCancelReason.USER_REQUESTED, operation.cancelReason);

        ManagedOperation broken = new ManagedOperation() {
            @Override
            public void onStarted(OperationHandle handle, MinecraftClient client) {
                throw new IllegalStateException("fixture");
            }
        };
        assertEquals(OperationStartResult.FAILED_TO_START,
                coordinator.start(null, descriptor("broken", ConnectionSnapshot.disconnected()), broken));
        assertFalse(coordinator.isBusy());
    }

    @Test
    void handleCompletionIsSingleUse() {
        OperationCoordinator coordinator = new OperationCoordinator(ignored -> ConnectionSnapshot.disconnected());
        RecordingOperation operation = new RecordingOperation();
        assertEquals(OperationStartResult.STARTED,
                coordinator.start(null, descriptor("activity", ConnectionSnapshot.disconnected()), operation));
        assertTrue(operation.handle.complete());
        assertFalse(operation.handle.complete());
        assertEquals(OperationCancelResult.NO_ACTIVE_OPERATION, coordinator.cancelActive(null));
    }

    @Test
    void offlineDryRunIsCapturedAndCannotTurnLiveMidOperation() {
        DMLSConfig.setDryRun(true);
        OperationCoordinator coordinator = new OperationCoordinator(ignored -> ConnectionSnapshot.disconnected());
        RecordingOperation operation = new RecordingOperation();

        assertEquals(OperationStartResult.STARTED,
                coordinator.start(null, "xray", "Xray", operation));
        assertTrue(operation.handle.descriptor().dryRunCaptured());
        assertEquals(CommandDispatch.SIMULATED,
                operation.handle.dispatchCommand(null, "co rollback u:test"));

        DMLSConfig.setDryRun(false);
        assertEquals(CommandDispatch.SIMULATED,
                operation.handle.dispatchCommand(null, "co rollback u:test"));
    }

    private static OperationDescriptor descriptor(String id, ConnectionSnapshot connection) {
        return new OperationDescriptor(id, id, connection, true);
    }

    private static final class RecordingOperation implements ManagedOperation {
        private OperationHandle handle;
        private int ticks;
        private OperationCancelReason cancelReason;

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            this.handle = handle;
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            ticks++;
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            cancelReason = reason;
        }
    }
}
