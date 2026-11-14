package com.duperknight.client.gui.widgets;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The compact row toggle used by Moderation View settings. */
public final class SettingsToggleWidget extends AbstractButton {
    private static final int HOVER_BACKGROUND = 0x80383838;
    private static final int BOX_BACKGROUND = 0xFF111111;
    private static final int BOX_BORDER = 0xFF888888;
    private static final int ENABLED_FILL = 0xFF55CC55;

    private final Font textRenderer;
    private final Consumer<Boolean> callback;
    private boolean checked;

    public SettingsToggleWidget(Font textRenderer, int x, int y, int width, Component label,
                                boolean checked, Consumer<Boolean> callback) {
        super(x, y, width, 20, label);
        this.textRenderer = textRenderer;
        this.checked = checked;
        this.callback = callback;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        checked = !checked;
        callback.accept(checked);
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (isHovered()) context.fill(getX(), getY(), getRight(), getBottom(), HOVER_BACKGROUND);
        int boxX = getX() + 5;
        int boxY = getY() + 4;
        context.fill(boxX, boxY, boxX + 10, boxY + 10, BOX_BACKGROUND);
        context.outline(boxX, boxY, 10, 10, BOX_BORDER);
        if (checked) context.fill(boxX + 2, boxY + 2, boxX + 8, boxY + 8, ENABLED_FILL);
        context.text(textRenderer, getMessage(), getX() + 21,
                getY() + (getHeight() - textRenderer.lineHeight) / 2, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, Component.translatable(
                checked ? "gui.narrate.checkbox.checked" : "gui.narrate.checkbox.unchecked", getMessage()));
        builder.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage"));
    }
}
