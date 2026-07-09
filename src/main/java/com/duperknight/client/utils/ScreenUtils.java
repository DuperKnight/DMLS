package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;
import java.util.Optional;

public final class ScreenUtils {
    private ScreenUtils() {
    }

    public static int slotIndex(int column, int row) {
        return (row - 1) * 9 + (column - 1);
    }

    public static void closeHandledScreen(MinecraftClient client) {
        if (client != null && client.player != null && client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
        }
    }

    public static int currentSyncId(MinecraftClient client) {
        if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
            return handledScreen.getScreenHandler().syncId;
        }
        return -1;
    }

    public static Optional<ScreenSnapshot> readSlot(MinecraftClient client, int slotIndex, String expectedTitle, int previousSyncId, int waitTicks) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
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

        if (handler.syncId == previousSyncId && waitTicks < 20) {
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

    public record ScreenSnapshot(String title, List<TooltipUtils.TooltipLine> tooltip) {
    }
}
