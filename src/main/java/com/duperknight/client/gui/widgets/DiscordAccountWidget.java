package com.duperknight.client.gui.widgets;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Transparent Discord account control with a one-row unlink shelf. */
public final class DiscordAccountWidget extends AbstractWidget {
    public static final int HEIGHT = 24;
    private static final int AVATAR_SIZE = 20;
    private static final int SHELF_GAP = 2;
    private static final int SHELF_HEIGHT = 20;

    private final Runnable unlinkAction;
    private Identifier avatarTexture;
    private boolean open;
    private boolean unlinking;

    public DiscordAccountWidget(int x, int y, int width, String displayName, Identifier avatarTexture,
                                Runnable unlinkAction) {
        super(x, y, width, HEIGHT, Component.literal(displayName));
        this.avatarTexture = avatarTexture;
        this.unlinkAction = unlinkAction;
    }

    public boolean isShelfOpen() {
        return open;
    }

    public void setAvatarTexture(Identifier avatarTexture) {
        this.avatarTexture = avatarTexture;
    }

    public void setUnlinking(boolean unlinking) {
        this.unlinking = unlinking;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (isHovered()) {
            context.fill(getX(), getY(), getRight(), getBottom(), 0x40FFFFFF);
            context.outline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            context.requestCursor(CursorTypes.POINTING_HAND);
        }

        int avatarX = getX() + 2;
        int avatarY = getY() + (getHeight() - AVATAR_SIZE) / 2;
        if (avatarTexture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, avatarTexture, avatarX, avatarY,
                    0.0F, 0.0F, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE);
        } else {
            context.fill(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE, 0xFF454545);
            String initial = getMessage().getString().isEmpty()
                    ? "?" : getMessage().getString().substring(0, 1).toUpperCase();
            Minecraft client = Minecraft.getInstance();
            context.centeredText(client.font, initial,
                    avatarX + AVATAR_SIZE / 2,
                    avatarY + (AVATAR_SIZE - client.font.lineHeight) / 2 + 1,
                    0xFFFFFFFF);
        }

        Minecraft client = Minecraft.getInstance();
        int textX = avatarX + AVATAR_SIZE + 5;
        int availableWidth = Math.max(1, getRight() - textX - 5);
        String label = client.font.plainSubstrByWidth(getMessage().getString(), availableWidth);
        context.text(client.font, label, textX,
                getY() + (getHeight() - client.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    public void renderShelf(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!visible || !open) return;
        int y = shelfY();
        boolean hovered = mouseX >= getX() && mouseX < getRight()
                && mouseY >= y && mouseY < y + SHELF_HEIGHT;
        context.nextStratum();
        context.fill(getX(), y, getRight(), y + SHELF_HEIGHT, DMLSMenuScreen.PANEL_BACKGROUND_COLOR);
        if (hovered && !unlinking) {
            context.fill(getX() + 1, y + 1, getRight() - 1, y + SHELF_HEIGHT - 1, 0x50FFFFFF);
            context.requestCursor(CursorTypes.POINTING_HAND);
        }
        context.outline(getX(), y, getWidth(), SHELF_HEIGHT,
                DMLSMenuScreen.PANEL_BORDER_COLOR);
        Component label = Component.translatable(unlinking
                ? "dmls.account.unlinking" : "dmls.account.unlink");
        Minecraft client = Minecraft.getInstance();
        context.centeredText(client.font, label, getX() + getWidth() / 2,
                y + (SHELF_HEIGHT - client.font.lineHeight) / 2 + 1,
                unlinking ? 0xFFAAAAAA : 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (open && click.button() == InputConstants.MOUSE_BUTTON_LEFT && click.x() >= getX() && click.x() < getRight()
                && click.y() >= shelfY() && click.y() < shelfY() + SHELF_HEIGHT) {
            if (!unlinking) unlinkAction.run();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        open = false;
        return false;
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        if (!unlinking) open = !open;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!isFocused()) return false;
        if (input.isEscape() && open) {
            open = false;
            return true;
        }
        if (input.isSelection()) {
            if (open && !unlinking) unlinkAction.run();
            else if (!unlinking) open = true;
            return true;
        }
        return super.keyPressed(input);
    }

    private int shelfY() {
        return getBottom() + SHELF_GAP;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
