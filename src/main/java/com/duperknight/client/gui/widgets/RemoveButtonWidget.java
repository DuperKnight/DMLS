package com.duperknight.client.gui.widgets;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.Button;

/** A compact remove button with a one-pixel visual correction for the vanilla font's × glyph. */
public final class RemoveButtonWidget extends Button.Plain {
    private static final net.minecraft.network.chat.Component LABEL = net.minecraft.network.chat.Component.literal("×");

    public RemoveButtonWidget(int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, LABEL, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractDefaultLabel(ActiveTextCollector textConsumer) {
        // Vanilla uses x + 2 through right - 2. Shift both bounds one pixel so
        // only the label moves; the button background and hitbox stay unchanged.
        textConsumer.acceptScrollingWithDefaultCenter(getMessage(), getX() + 3, getRight() - 1, getY(), getBottom());
    }
}