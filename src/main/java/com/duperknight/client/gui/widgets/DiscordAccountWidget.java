package com.duperknight.client.gui.widgets;

import com.duperknight.client.gui.DMLSMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Transparent Discord account control with a one-row unlink shelf. */
public final class DiscordAccountWidget extends ClickableWidget {
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
        super(x, y, width, HEIGHT, Text.literal(displayName));
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
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isHovered()) {
            context.fill(getX(), getY(), getRight(), getBottom(), 0x40FFFFFF);
            context.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            context.setCursor(StandardCursors.POINTING_HAND);
        }

        int avatarX = getX() + 2;
        int avatarY = getY() + (getHeight() - AVATAR_SIZE) / 2;
        if (avatarTexture != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, avatarTexture, avatarX, avatarY,
                    0.0F, 0.0F, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE);
        } else {
            context.fill(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE, 0xFF454545);
            String initial = getMessage().getString().isEmpty()
                    ? "?" : getMessage().getString().substring(0, 1).toUpperCase();
            MinecraftClient client = MinecraftClient.getInstance();
            context.drawCenteredTextWithShadow(client.textRenderer, initial,
                    avatarX + AVATAR_SIZE / 2,
                    avatarY + (AVATAR_SIZE - client.textRenderer.fontHeight) / 2 + 1,
                    0xFFFFFFFF);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int textX = avatarX + AVATAR_SIZE + 5;
        int availableWidth = Math.max(1, getRight() - textX - 5);
        String label = client.textRenderer.trimToWidth(getMessage().getString(), availableWidth);
        context.drawTextWithShadow(client.textRenderer, label, textX,
                getY() + (getHeight() - client.textRenderer.fontHeight) / 2 + 1, 0xFFFFFFFF);
    }

    public void renderShelf(DrawContext context, int mouseX, int mouseY) {
        if (!visible || !open) return;
        int y = shelfY();
        boolean hovered = mouseX >= getX() && mouseX < getRight()
                && mouseY >= y && mouseY < y + SHELF_HEIGHT;
        context.createNewRootLayer();
        context.fill(getX(), y, getRight(), y + SHELF_HEIGHT, DMLSMenuScreen.PANEL_BACKGROUND_COLOR);
        if (hovered && !unlinking) {
            context.fill(getX() + 1, y + 1, getRight() - 1, y + SHELF_HEIGHT - 1, 0x50FFFFFF);
            context.setCursor(StandardCursors.POINTING_HAND);
        }
        context.drawStrokedRectangle(getX(), y, getWidth(), SHELF_HEIGHT,
                DMLSMenuScreen.PANEL_BORDER_COLOR);
        Text label = Text.translatable(unlinking
                ? "dmls.account.unlinking" : "dmls.account.unlink");
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawCenteredTextWithShadow(client.textRenderer, label, getX() + getWidth() / 2,
                y + (SHELF_HEIGHT - client.textRenderer.fontHeight) / 2 + 1,
                unlinking ? 0xFFAAAAAA : 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (open && click.button() == 0 && click.x() >= getX() && click.x() < getRight()
                && click.y() >= shelfY() && click.y() < shelfY() + SHELF_HEIGHT) {
            if (!unlinking) unlinkAction.run();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        open = false;
        return false;
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        if (!unlinking) open = !open;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!isFocused()) return false;
        if (input.isEscape() && open) {
            open = false;
            return true;
        }
        if (input.isEnterOrSpace()) {
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
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
