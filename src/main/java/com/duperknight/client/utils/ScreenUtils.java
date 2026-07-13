package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;
import java.util.Optional;

/**
 * Utility methods for interacting with the Minecraft screen and inventory.
 */
public final class ScreenUtils {
    private ScreenUtils() {
    }

    /**
     * Converts a slot position (column, row) to an index in the inventory.
     * @param column 1-based column index
     * @param row 1-based row index
     * @return the slot index
     */
    public static int slotIndex(int column, int row) {
        return (row - 1) * 9 + (column - 1);
    }

    /**
     * Closes the current screen if it's a {@link HandledScreen}.
     *
     * @param client the Minecraft client
     */
    public static void closeHandledScreen(MinecraftClient client) {
        if (client != null && client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        }
    }

    /**
     * Gets the sync ID of the current screen if it's a {@link HandledScreen}.
     *
     * @param client the Minecraft client
     * @return the sync ID, or -1 if not applicable
     */
    public static int currentSyncId(MinecraftClient client) {
        if (client != null && client.currentScreen instanceof HandledScreen<?> handledScreen) {
            return handledScreen.getScreenHandler().syncId;
        }
        return -1;
    }

    /**
     * Reads the contents of a slot in the current screen.
     *
     * @param client the Minecraft client
     * @param slotIndex 0-based slot index
     * @param expectedTitle the expected title of the screen
     * @param previousSyncId the previous sync ID of the screen
     * @param waitTicks the number of ticks since the last sync
     * @return the slot contents, if available
     */
    public static Optional<ScreenSnapshot> readSlot(MinecraftClient client, int slotIndex, String expectedTitle, int previousSyncId, int waitTicks) {
        if (client == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return Optional.empty();
        }

        ScreenHandler handler = handledScreen.getScreenHandler();
        if (handler.slots.size() <= slotIndex) {
            return Optional.empty();
        }

        String title = ChatUtils.cleanLine(client.currentScreen.getTitle().getString());
        if (!title.equalsIgnoreCase(expectedTitle)) {
            return Optional.empty();
        }

        // Never read a same-title menu that was already open before dispatch. If the
        // server does not replace it, the query times out instead of trusting stale data.
        if (!isReplacementSyncId(previousSyncId, handler.syncId)) {
            return Optional.empty();
        }

        Slot slot = handler.getSlot(slotIndex);
        if (!slot.hasStack()) {
            return Optional.empty();
        }

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        List<TooltipUtils.TooltipLine> tooltip = Screen.getTooltipFromItem(client, stack)
                .stream()
                .map(TooltipUtils::toTooltipLine)
                .filter(line -> !line.text().isEmpty())
                .toList();

        return Optional.of(new ScreenSnapshot(title, tooltip));
    }

    static boolean isReplacementSyncId(int previousSyncId, int currentSyncId) {
        return previousSyncId != currentSyncId;
    }

    /**
     * Represents a snapshot of the current screen.
     * @param title
     * @param tooltip
     */
    public record ScreenSnapshot(String title, List<TooltipUtils.TooltipLine> tooltip) {
    }
}
