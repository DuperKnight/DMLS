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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityWaveOperationTest {
    @Test
    void preparationDeduplicatesSkipsAndCapsTheWave() {
        ActivityWaveModule.ActivityRequest request = ActivityWaveModule.prepare(
                "Alice alice Bob invalid-name xx");
        assertEquals(ActivityWaveModule.PreparationStatus.VALID, request.status());
        assertEquals(List.of("Alice", "Bob"), request.usernames());
        assertEquals(List.of("invalid-name", "xx"), request.skipped());

        String tooMany = IntStream.range(0, ActivityWaveModule.MAX_PLAYERS + 1)
                .mapToObj(index -> "u%02d".formatted(index)).reduce((a, b) -> a + " " + b).orElseThrow();
        assertEquals(ActivityWaveModule.PreparationStatus.TOO_MANY,
                ActivityWaveModule.prepare(tooMany).status());
        assertEquals(ActivityWaveModule.PreparationStatus.EMPTY,
                ActivityWaveModule.prepare("bad-name xx").status());
    }

    @Test
    void simulatedWavePacesEveryPlayerAndNeverFabricatesHours() {
        RecordingListener listener = new RecordingListener();
        ActivityWaveOperation operation = new ActivityWaveOperation(
                List.of("Alice", "Bob", "Carol"), listener,
                (handle, client, command) -> CommandDispatch.SIMULATED);
        OperationCoordinator coordinator = start(operation);

        tick(operation, ActivityWaveOperation.COMMAND_GAP_TICKS * 2);

        assertFalse(coordinator.isBusy());
        assertTrue(listener.finished.completed());
        assertNull(listener.cancelReason);
        assertEquals(List.of("Alice", "Bob", "Carol"), new ArrayList<>(listener.finished.results().keySet()));
        assertTrue(listener.finished.results().values().stream()
                .allMatch(value -> value.kind() == ActivityWaveOperation.ResultKind.SIMULATED));
    }

    @Test
    void onePlayerTimingOutDoesNotBlockLaterConfirmedResults() {
        RecordingListener listener = new RecordingListener();
        AtomicInteger dispatches = new AtomicInteger();
        ActivityWaveOperation operation = new ActivityWaveOperation(
                List.of("Alice", "Bob"), listener,
                (handle, client, command) -> {
                    dispatches.incrementAndGet();
                    return CommandDispatch.SENT;
                });
        OperationCoordinator coordinator = start(operation);

        tick(operation, ActivityWaveOperation.RESPONSE_TIMEOUT_TICKS);
        assertEquals(2, dispatches.get());
        assertNull(listener.finished);
        assertEquals("Bob", listener.progress.getLast());

        message(operation, "Activity for player Bob in the last 30 days");
        message(operation, "1,500 minutes or 25.5 hours");

        assertFalse(coordinator.isBusy());
        assertEquals(ActivityWaveOperation.ResultKind.NO_RESPONSE,
                listener.finished.results().get("Alice").kind());
        assertEquals(ActivityWaveOperation.ResultKind.HOURS,
                listener.finished.results().get("Bob").kind());
        assertEquals(25.5, listener.finished.results().get("Bob").hours());
    }

    @Test
    void cancellationAndBlockedFollowupPreservePartialResults() {
        RecordingListener cancelledListener = new RecordingListener();
        ActivityWaveOperation cancelled = new ActivityWaveOperation(
                List.of("Alice", "Bob"), cancelledListener,
                (handle, client, command) -> CommandDispatch.SENT);
        OperationCoordinator firstCoordinator = start(cancelled);
        confirm(cancelled, "Alice", 12.5);
        firstCoordinator.cancel(ActivityWaveModule.OPERATION_ID, null);

        assertEquals(OperationCancelReason.MODULE_REQUESTED, cancelledListener.cancelReason);
        assertEquals(List.of("Alice"), new ArrayList<>(cancelledListener.cancelled.results().keySet()));

        RecordingListener blockedListener = new RecordingListener();
        AtomicInteger attempts = new AtomicInteger();
        ActivityWaveOperation blocked = new ActivityWaveOperation(
                List.of("Alice", "Bob"), blockedListener,
                (handle, client, command) -> attempts.getAndIncrement() == 0
                        ? CommandDispatch.SENT : CommandDispatch.BLOCKED);
        OperationCoordinator secondCoordinator = start(blocked);
        confirm(blocked, "Alice", 12.5);
        tick(blocked, ActivityWaveOperation.COMMAND_GAP_TICKS);

        assertFalse(secondCoordinator.isBusy());
        assertEquals(OperationCancelReason.DISPATCH_BLOCKED, blockedListener.cancelReason);
        assertEquals(List.of("Alice"), new ArrayList<>(blockedListener.cancelled.results().keySet()));
    }

    private static OperationCoordinator start(ActivityWaveOperation operation) {
        OperationCoordinator coordinator = new OperationCoordinator();
        OperationDescriptor descriptor = new OperationDescriptor(ActivityWaveModule.OPERATION_ID,
                "Activity Wave", ConnectionSnapshot.disconnected(), true);
        assertEquals(OperationStartResult.STARTED, coordinator.start(null, descriptor, operation));
        return coordinator;
    }

    private static void tick(ActivityWaveOperation operation, int count) {
        for (int tick = 0; tick < count; tick++) operation.onTick(null, null);
    }

    private static void confirm(ActivityWaveOperation operation, String username, double hours) {
        message(operation, "Activity for player " + username + " in the last 30 days");
        message(operation, "100 minutes or " + hours + " hours");
    }

    private static void message(ActivityWaveOperation operation, String line) {
        operation.onServerMessage(null, null,
                new ServerMessage(null, line, MessageOrigin.SERVER_SYSTEM, false, 0));
    }

    private static final class RecordingListener implements ActivityWaveOperation.Listener {
        private final List<String> progress = new ArrayList<>();
        private ActivityWaveOperation.Summary finished;
        private ActivityWaveOperation.Summary cancelled;
        private OperationCancelReason cancelReason;

        @Override public void started(MinecraftClient client, int playerCount) { }

        @Override
        public void progress(MinecraftClient client, String username, int playerIndex, int playerCount) {
            progress.add(username);
        }

        @Override
        public void finished(MinecraftClient client, ActivityWaveOperation.Summary summary) {
            finished = summary;
        }

        @Override
        public void cancelled(MinecraftClient client, ActivityWaveOperation.Summary summary,
                              OperationCancelReason reason) {
            cancelled = summary;
            cancelReason = reason;
        }
    }
}
