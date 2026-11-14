package com.duperknight.client.utils;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
     * Closes the current screen if it's a {@link AbstractContainerScreen}.
     *
     * @param client the Minecraft client
     */
    public static void closeHandledScreen(Minecraft client) {
        if (client != null && client.player != null && client.gui.screen() instanceof AbstractContainerScreen<?>) {
            client.player.closeContainer();
        }
    }

    /**
     * Gets the sync ID of the current screen if it's a {@link AbstractContainerScreen}.
     *
     * @param client the Minecraft client
     * @return the sync ID, or -1 if not applicable
     */
    public static int currentSyncId(Minecraft client) {
        if (client != null && client.gui.screen() instanceof AbstractContainerScreen<?> handledScreen) {
            return handledScreen.getMenu().containerId;
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
    public static Optional<ScreenSnapshot> readSlot(Minecraft client, int slotIndex, String expectedTitle, int previousSyncId, int waitTicks) {
        if (client == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> handledScreen)) {
            return Optional.empty();
        }

        AbstractContainerMenu handler = handledScreen.getMenu();
        if (handler.slots.size() <= slotIndex) {
            return Optional.empty();
        }

        String title = ChatUtils.cleanLine(client.gui.screen().getTitle().getString());
        if (!title.equalsIgnoreCase(expectedTitle)) {
            return Optional.empty();
        }

        // Never read a same-title menu that was already open before dispatch. If the
        // server does not replace it, the query times out instead of trusting stale data.
        if (!isReplacementSyncId(previousSyncId, handler.containerId)) {
            return Optional.empty();
        }

        Slot slot = handler.getSlot(slotIndex);
        if (!slot.hasItem()) {
            return Optional.empty();
        }

        ItemStack stack = slot.getItem();
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
