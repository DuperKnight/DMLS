package com.duperknight.client.modules;

import java.util.List;
import java.util.Optional;

/** Small client-tick scheduler used by the simultaneous-command module. */
final class TickSpacedCommandQueue {
    private final List<String> commands;
    private final int gapTicks;
    private int nextIndex;
    private int ticksUntilNext;

    TickSpacedCommandQueue(List<String> commands, int gapTicks) {
        this.commands = List.copyOf(commands);
        if (gapTicks < 1) throw new IllegalArgumentException("gapTicks");
        this.gapTicks = gapTicks;
        this.ticksUntilNext = gapTicks;
    }

    Optional<String> tick() {
        if (isEmpty()) return Optional.empty();
        if (--ticksUntilNext > 0) return Optional.empty();

        String command = commands.get(nextIndex++);
        ticksUntilNext = gapTicks;
        return Optional.of(command);
    }

    boolean isEmpty() {
        return nextIndex >= commands.size();
    }
}
