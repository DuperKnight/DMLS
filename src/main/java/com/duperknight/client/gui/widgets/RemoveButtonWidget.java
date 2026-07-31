package com.duperknight.client.gui.widgets;

import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.widget.ButtonWidget;

/** A compact remove button with a one-pixel visual correction for the vanilla font's × glyph. */
public final class RemoveButtonWidget extends ButtonWidget.Text {
    private static final net.minecraft.text.Text LABEL = net.minecraft.text.Text.literal("×");

    public RemoveButtonWidget(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, LABEL, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawLabel(DrawnTextConsumer textConsumer) {
        // Vanilla uses x + 2 through right - 2. Shift both bounds one pixel so
        // only the label moves; the button background and hitbox stay unchanged.
        textConsumer.text(getMessage(), getX() + 3, getRight() - 1, getY(), getBottom());
    }
}
