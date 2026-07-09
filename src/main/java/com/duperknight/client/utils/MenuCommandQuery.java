package com.duperknight.client.utils;

import com.duperknight.client.utils.TooltipUtils.TooltipLine;
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

    private int previousSyncId = -1;
    private int waitTicks;
    private boolean started;

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
        this.command = command;
        this.expectedTitle = expectedTitle;
        this.timeoutTicks = timeoutTicks;
        this.slotIndexes = Arrays.stream(slotIndexes).boxed().toList();
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
    public void start(MinecraftClient client) {
        previousSyncId = ScreenUtils.currentSyncId(client);
        waitTicks = 0;
        started = true;
        ClientUtils.sendCommand(client, command);
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

        waitTicks++;
        if (waitTicks > timeoutTicks) {
            return TickResult.timedOut();
        }

        Map<Integer, List<TooltipLine>> slotTooltips = new LinkedHashMap<>();
        String title = null;
        for (int slotIndex : slotIndexes) {
            Optional<ScreenUtils.ScreenSnapshot> snapshot = ScreenUtils.readSlot(client, slotIndex, expectedTitle, previousSyncId, waitTicks);
            if (snapshot.isEmpty()) {
                return TickResult.waiting();
            }

            title = snapshot.get().title();
            slotTooltips.put(slotIndex, snapshot.get().tooltip());
        }

        return TickResult.ready(new Result(command, title == null ? expectedTitle : title, slotTooltips));
    }

    /**
     * Represents the current status of a menu query operation.
     */
    public enum Status {
        WAITING,
        READY,
        TIMED_OUT
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
        public Optional<List<TooltipLine>> tooltip(int slotIndex) {
            return Optional.ofNullable(slotTooltips.get(slotIndex));
        }
    }
}
