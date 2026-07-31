package com.duperknight.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** The compact row toggle used by Moderation View settings. */
public final class SettingsToggleWidget extends PressableWidget {
    private static final int HOVER_BACKGROUND = 0x80383838;
    private static final int BOX_BACKGROUND = 0xFF111111;
    private static final int BOX_BORDER = 0xFF888888;
    private static final int ENABLED_FILL = 0xFF55CC55;

    private final TextRenderer textRenderer;
    private final Consumer<Boolean> callback;
    private boolean checked;

    public SettingsToggleWidget(TextRenderer textRenderer, int x, int y, int width, Text label,
                                boolean checked, Consumer<Boolean> callback) {
        super(x, y, width, 20, label);
        this.textRenderer = textRenderer;
        this.checked = checked;
        this.callback = callback;
    }

    @Override
    public void onPress(AbstractInput input) {
        checked = !checked;
        callback.accept(checked);
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isHovered()) context.fill(getX(), getY(), getRight(), getBottom(), HOVER_BACKGROUND);
        int boxX = getX() + 5;
        int boxY = getY() + 4;
        context.fill(boxX, boxY, boxX + 10, boxY + 10, BOX_BACKGROUND);
        context.drawStrokedRectangle(boxX, boxY, 10, 10, BOX_BORDER);
        if (checked) context.fill(boxX + 2, boxY + 2, boxX + 8, boxY + 8, ENABLED_FILL);
        context.drawTextWithShadow(textRenderer, getMessage(), getX() + 21,
                getY() + (getHeight() - textRenderer.fontHeight) / 2, 0xFFFFFFFF);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, Text.translatable(
                checked ? "gui.narrate.checkbox.checked" : "gui.narrate.checkbox.unchecked", getMessage()));
        builder.put(NarrationPart.USAGE, Text.translatable("narration.checkbox.usage"));
    }
}
