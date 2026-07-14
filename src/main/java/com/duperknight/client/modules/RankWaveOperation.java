package com.duperknight.client.modules;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.LuckPermsResponseParser;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.ResponseStatus;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared fail-closed LuckPerms sequence used by promotion and demotion waves. */
final class RankWaveOperation implements ManagedOperation {
    static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;
    static final int COMMAND_GAP_TICKS = 20 * 3;

    record Step(String username, String command, LuckPermsResponseParser.Action action,
                String group, boolean completesPlayer) {
    }

    record Summary(List<String> confirmedPlayers, List<String> simulatedPlayers,
                   int processedCommands, int totalCommands, PacedCommandSequence.State state,
                   Step interruptedStep) {
    }

    interface Listener {
        void started(MinecraftClient client, int playerCount);

        void progress(MinecraftClient client, Step step, int commandIndex, int commandCount);

        void finished(MinecraftClient client, Summary summary);

        void cancelled(MinecraftClient client, Summary summary, OperationCancelReason reason);
    }

    private final List<Step> steps;
    private final int playerCount;
    private final Listener listener;
    private final List<String> confirmedPlayers = new ArrayList<>();
    private final List<String> simulatedPlayers = new ArrayList<>();

    private OperationHandle handle;
    private MinecraftClient client;
    private PacedCommandSequence<Step> sequence;
    private int synchronizedSteps;
    private int announcedIndex = -1;
    private boolean finished;

    RankWaveOperation(List<Step> steps, int playerCount, Listener listener) {
        this.steps = List.copyOf(steps);
        this.playerCount = playerCount;
        this.listener = Objects.requireNonNull(listener);
    }

    static Step step(String username, String command, boolean completesPlayer) {
        String addMarker = " parent add ";
        String removeMarker = " parent remove ";
        String marker = command.contains(addMarker) ? addMarker : removeMarker;
        LuckPermsResponseParser.Action action = marker.equals(addMarker)
                ? LuckPermsResponseParser.Action.ADD : LuckPermsResponseParser.Action.REMOVE;
        int markerIndex = command.indexOf(marker);
        if (markerIndex < 0) throw new IllegalArgumentException("Unsupported LuckPerms command: " + command);
        return new Step(username, command, action, command.substring(markerIndex + marker.length()), completesPlayer);
    }

    boolean acceptedAtStart() {
        return sequence != null && sequence.state() != PacedCommandSequence.State.BLOCKED
                && sequence.state() != PacedCommandSequence.State.FAILED;
    }

    @Override
    public void onStarted(OperationHandle handle, MinecraftClient client) {
        this.handle = handle;
        this.client = client;
        sequence = new PacedCommandSequence<>(steps, COMMAND_GAP_TICKS, RESPONSE_TIMEOUT_TICKS,
                step -> handle.dispatchCommand(client, step.command()),
                (step, line) -> switch (LuckPermsResponseParser.parseParentChange(
                        step.action(), step.username(), step.group(), line)) {
                    case CONFIRMED -> ResponseStatus.CONFIRMED;
                    case REJECTED -> ResponseStatus.REJECTED;
                    case UNRELATED -> ResponseStatus.UNRELATED;
                });
        listener.started(client, playerCount);
        announceCurrent();
        sequence.start();
        synchronizeAndFinishIfNeeded();
    }

    @Override
    public void onTick(OperationHandle handle, MinecraftClient client) {
        sequence.tick();
        synchronizeAndFinishIfNeeded();
        announceCurrent();
    }

    @Override
    public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
        if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
        sequence.accept(message.cleanText());
        synchronizeAndFinishIfNeeded();
        announceCurrent();
    }

    @Override
    public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
        if (sequence != null) sequence.cancel();
        synchronizeCompletedSteps();
        listener.cancelled(client, summary(), reason);
        finished = true;
    }

    private void synchronizeAndFinishIfNeeded() {
        synchronizeCompletedSteps();
        if (!finished && sequence.state().terminal()) {
            finished = true;
            listener.finished(client, summary());
            handle.complete();
        }
    }

    private void synchronizeCompletedSteps() {
        while (sequence != null && synchronizedSteps < sequence.processedCount()) {
            Step completed = steps.get(synchronizedSteps);
            if (completed.completesPlayer()) {
                if (synchronizedSteps < sequence.confirmedCount()) confirmedPlayers.add(completed.username());
                else simulatedPlayers.add(completed.username());
            }
            synchronizedSteps++;
        }
    }

    private void announceCurrent() {
        if (sequence == null || sequence.state().terminal()) return;
        int index = sequence.currentIndex();
        if (index == announcedIndex || index >= steps.size()) return;
        announcedIndex = index;
        listener.progress(client, steps.get(index), index + 1, steps.size());
    }

    private Summary summary() {
        Step interrupted = sequence == null ? null : sequence.currentStep().orElse(null);
        return new Summary(List.copyOf(confirmedPlayers), List.copyOf(simulatedPlayers),
                sequence == null ? 0 : sequence.processedCount(), steps.size(),
                sequence == null ? PacedCommandSequence.State.FAILED : sequence.state(), interrupted);
    }
}
