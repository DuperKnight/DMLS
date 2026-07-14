package com.duperknight.client.utils;

import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends a command and waits for the expected menu to open, then reads tooltip data
 * from the configured slots.
 *
 * <p>This helper is tick-based because Minecraft menus do not appear immediately
 * after sending a command. Call {@link #start(MinecraftClient)} once, then call
 * {@link #tick(MinecraftClient)} every client tick until it returns READY or
 * TIMED_OUT.
 */
public final class MenuCommandQuery {
    private final String command;
    private final String expectedTitle;
    private final int timeoutTicks;
    private final List<Integer> slotIndexes;
    private final Environment environment;

    private int previousSyncId = -1;
    private int waitTicks;
    private boolean started;
    private boolean completed;
    private ConnectionSnapshot connection = ConnectionSnapshot.disconnected();
    private TickResult terminalResult;

    /**
     * Creates a query that sends a command, waits for a matching menu title, and reads
     * tooltip data from the requested slots.
     *
     * @param command the command to send without the leading slash
     * @param expectedTitle the menu title expected after the command runs
     * @param timeoutTicks how many client ticks to wait before timing out
     * @param slotIndexes the menu slot indexes to read when the menu opens
     */
    public MenuCommandQuery(String command, String expectedTitle, int timeoutTicks, int... slotIndexes) {
        this(command, expectedTitle, timeoutTicks, Environment.DEFAULT, slotIndexes);
    }

    MenuCommandQuery(String command, String expectedTitle, int timeoutTicks,
                     Environment environment, int... slotIndexes) {
        this.command = command;
        this.expectedTitle = expectedTitle;
        this.timeoutTicks = timeoutTicks;
        this.slotIndexes = Arrays.stream(slotIndexes).boxed().toList();
        this.environment = java.util.Objects.requireNonNull(environment, "environment");
    }

    /**
     * Returns the command associated with this query.
     *
     * @return the command
     */
    public String command() {
        return command;
    }

    /**
     * Starts the query by sending the command and initializing the waiting state.
     *
     * @param client the Minecraft client
     */
    public CommandDispatch start(MinecraftClient client) {
        return start(client, ClientUtils::dispatchCommand);
    }

    /** Starts using an operation-owned dispatcher with captured dry-run and connection state. */
    public CommandDispatch start(MinecraftClient client, CommandDispatcher dispatcher) {
        previousSyncId = environment.currentSyncId(client);
        waitTicks = 0;
        started = true;
        connection = environment.connection(client);
        CommandDispatch dispatch = dispatcher.dispatch(client, command);
        if (dispatch == CommandDispatch.BLOCKED) {
            completed = true;
            terminalResult = TickResult.cancelled();
        } else if (dispatch == CommandDispatch.SIMULATED) {
            completed = true;
            terminalResult = TickResult.simulated();
        }
        return dispatch;
    }

    /**
     * Ticks the query, reading the menu title and tooltip data from the specified slots.
     *
     * @param client the Minecraft client
     * @return the result of the tick operation
     */
    public TickResult tick(MinecraftClient client) {
        if (!started) {
            start(client);
        }
        if (completed) return terminalResult;
        if (!connection.sameConnection(environment.connection(client))) {
            completed = true;
            return terminalResult = TickResult.cancelled();
        }

        waitTicks++;
        if (waitTicks > timeoutTicks) {
            completed = true;
            return terminalResult = TickResult.timedOut();
        }

        Map<Integer, List<TooltipLine>> slotTooltips = new LinkedHashMap<>();
        String title = null;
        for (int slotIndex : slotIndexes) {
            Optional<ScreenUtils.ScreenSnapshot> snapshot = environment.readSlot(
                    client, slotIndex, expectedTitle, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return TickResult.waiting();
            }

            title = snapshot.get().title();
            slotTooltips.put(slotIndex, snapshot.get().tooltip());
        }

        completed = true;
        return terminalResult = TickResult.ready(new Result(command, title == null ? expectedTitle : title, slotTooltips));
    }

    /**
     * Represents the current status of a menu query operation.
     */
    public enum Status {
        WAITING,
        READY,
        TIMED_OUT,
        CANCELLED,
        SIMULATED
    }

    /**
     * Represents the result of a single tick operation during a menu query.
     *
     * @param status the current status of the query operation
     * @param result the query result data, present only when status is READY
     */
    public record TickResult(Status status, Optional<Result> result) {
        /**
         * Creates a TickResult indicating the query is still waiting for the menu to open.
         *
         * @return a TickResult with WAITING status
         */
        private static TickResult waiting() {
            return new TickResult(Status.WAITING, Optional.empty());
        }

        /**
         * Creates a TickResult indicating the query has timed out.
         *
         * @return a TickResult with TIMED_OUT status
         */
        private static TickResult timedOut() {
            return new TickResult(Status.TIMED_OUT, Optional.empty());
        }

        private static TickResult cancelled() {
            return new TickResult(Status.CANCELLED, Optional.empty());
        }

        private static TickResult simulated() {
            return new TickResult(Status.SIMULATED, Optional.empty());
        }

        /**
         * Creates a TickResult indicating the query has completed successfully.
         *
         * @param result the query result containing menu data
         * @return a TickResult with READY status and the result data
         */
        private static TickResult ready(Result result) {
            return new TickResult(Status.READY, Optional.of(result));
        }
    }

    /**
     * Represents the result of a menu query operation.
     *
     * @param command the command associated with the menu
     * @param title the title of the menu
     * @param slotTooltips the tooltips for each slot in the menu
     */
    public record Result(String command, String title, Map<Integer, List<TooltipLine>> slotTooltips) {
        public Result {
            Map<Integer, List<TooltipLine>> snapshot = new LinkedHashMap<>();
            slotTooltips.forEach((slot, tooltip) -> snapshot.put(slot, List.copyOf(tooltip)));
            slotTooltips = Map.copyOf(snapshot);
        }

        public Optional<List<TooltipLine>> tooltip(int slotIndex) {
            return Optional.ofNullable(slotTooltips.get(slotIndex));
        }
    }

    @FunctionalInterface
    public interface CommandDispatcher {
        CommandDispatch dispatch(MinecraftClient client, String command);
    }

    interface Environment {
        Environment DEFAULT = new Environment() {
            @Override
            public int currentSyncId(MinecraftClient client) {
                return ScreenUtils.currentSyncId(client);
            }

            @Override
            public ConnectionSnapshot connection(MinecraftClient client) {
                return ConnectionSnapshot.capture(client);
            }

            @Override
            public Optional<ScreenUtils.ScreenSnapshot> readSlot(MinecraftClient client, int slotIndex,
                                                                  String expectedTitle, int previousSyncId,
                                                                  int waitTicks) {
                return ScreenUtils.readSlot(client, slotIndex, expectedTitle, previousSyncId, waitTicks);
            }
        };

        int currentSyncId(MinecraftClient client);

        ConnectionSnapshot connection(MinecraftClient client);

        Optional<ScreenUtils.ScreenSnapshot> readSlot(MinecraftClient client, int slotIndex,
                                                       String expectedTitle, int previousSyncId, int waitTicks);
    }
}
