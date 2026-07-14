package com.duperknight.client.modules;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationDescriptor;
import com.duperknight.client.session.OperationStartResult;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonorPetOperationTest {
    @Test
    void preparationFreezesValidatedCommand() {
        DonorPetModule.DonorPetRequest request = DonorPetModule.prepare(" Alice ", "GRIFFON");
        assertEquals(DonorPetModule.PreparationStatus.VALID, request.status());
        assertEquals("Alice", request.username());
        assertEquals("griffon", request.pet());
        assertEquals("lp user Alice permission set mcpets.elitemountvol6whitegriffon true", request.command());

        assertEquals(DonorPetModule.PreparationStatus.INVALID_USERNAME,
                DonorPetModule.prepare("bad-name", "griffon").status());
        assertEquals(DonorPetModule.PreparationStatus.UNKNOWN_PET,
                DonorPetModule.prepare("Alice", "dragon").status());
    }

    @Test
    void reportsConfirmedRejectedSimulatedAndBlockedSeparately() {
        RecordingListener simulated = run(CommandDispatch.SIMULATED);
        assertEquals(DonorPetOperation.Outcome.SIMULATED, simulated.finished.outcome());

        RecordingListener blocked = run(CommandDispatch.BLOCKED);
        assertEquals(DonorPetOperation.Outcome.BLOCKED, blocked.finished.outcome());

        Running confirmed = running(CommandDispatch.SENT);
        message(confirmed.operation, "[LP] Set " + confirmed.request.permission()
                + " to true for Alice in context global.");
        assertEquals(DonorPetOperation.Outcome.CONFIRMED, confirmed.listener.finished.outcome());
        assertEquals(1, confirmed.listener.waiting);

        Running rejected = running(CommandDispatch.SENT);
        message(rejected.operation, "[LP] User Alice could not be found.");
        assertEquals(DonorPetOperation.Outcome.REJECTED, rejected.listener.finished.outcome());
    }

    @Test
    void unknownResponseTimesOutAsSentUnverified() {
        Running running = running(CommandDispatch.SENT);
        message(running.operation, "Permission updated successfully.");
        for (int tick = 0; tick < DonorPetOperation.RESPONSE_TIMEOUT_TICKS; tick++) {
            running.operation.onTick(null, null);
        }

        assertEquals(DonorPetOperation.Outcome.SENT_UNVERIFIED, running.listener.finished.outcome());
        assertFalse(running.coordinator.isBusy());
    }

    @Test
    void donorOperationOwnsGlobalSlotAndCancellationIsTruthful() {
        Running donor = running(CommandDispatch.SENT);
        ActivityWaveOperation other = new ActivityWaveOperation(
                java.util.List.of("Bob"), new EmptyActivityListener(),
                (handle, client, command) -> CommandDispatch.SENT);
        OperationDescriptor otherDescriptor = new OperationDescriptor(ActivityWaveModule.OPERATION_ID,
                "Activity Wave", ConnectionSnapshot.disconnected(), true);
        assertEquals(OperationStartResult.BUSY,
                donor.coordinator.start(null, otherDescriptor, other));

        donor.coordinator.cancel(DonorPetModule.OPERATION_ID, null);
        assertEquals(DonorPetOperation.Outcome.CANCELLED, donor.listener.cancelled.outcome());
        assertEquals(OperationCancelReason.MODULE_REQUESTED, donor.listener.cancelReason);
        assertFalse(donor.coordinator.isBusy());
    }

    private static RecordingListener run(CommandDispatch dispatch) {
        Running running = running(dispatch);
        assertFalse(running.coordinator.isBusy());
        return running.listener;
    }

    private static Running running(CommandDispatch dispatch) {
        DonorPetModule.DonorPetRequest request = DonorPetModule.prepare("Alice", "griffon");
        RecordingListener listener = new RecordingListener();
        DonorPetOperation operation = new DonorPetOperation(request, listener,
                (handle, client, command) -> dispatch);
        OperationCoordinator coordinator = new OperationCoordinator();
        OperationDescriptor descriptor = new OperationDescriptor(DonorPetModule.OPERATION_ID,
                "Donor Pets", ConnectionSnapshot.disconnected(), true);
        assertEquals(OperationStartResult.STARTED, coordinator.start(null, descriptor, operation));
        return new Running(request, operation, coordinator, listener);
    }

    private static void message(DonorPetOperation operation, String line) {
        operation.onServerMessage(null, null,
                new ServerMessage(null, line, MessageOrigin.SERVER_SYSTEM, false, 0));
    }

    private record Running(DonorPetModule.DonorPetRequest request, DonorPetOperation operation,
                           OperationCoordinator coordinator, RecordingListener listener) {
    }

    private static final class RecordingListener implements DonorPetOperation.Listener {
        private int waiting;
        private DonorPetOperation.Summary finished;
        private DonorPetOperation.Summary cancelled;
        private OperationCancelReason cancelReason;

        @Override public void waiting(MinecraftClient client, DonorPetModule.DonorPetRequest request) { waiting++; }
        @Override public void finished(MinecraftClient client, DonorPetOperation.Summary summary) { finished = summary; }
        @Override public void cancelled(MinecraftClient client, DonorPetOperation.Summary summary,
                                        OperationCancelReason reason) {
            cancelled = summary;
            cancelReason = reason;
        }
    }

    private static final class EmptyActivityListener implements ActivityWaveOperation.Listener {
        @Override public void started(MinecraftClient client, int playerCount) { }
        @Override public void progress(MinecraftClient client, String username, int index, int count) { }
        @Override public void finished(MinecraftClient client, ActivityWaveOperation.Summary summary) { }
        @Override public void cancelled(MinecraftClient client, ActivityWaveOperation.Summary summary,
                                        OperationCancelReason reason) { }
    }
}
