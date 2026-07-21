package com.duperknight.client.gui.widgets;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Masked link-code field with an inset copy button. */
public final class LinkCodeWidget extends ClickableWidget {
    private static final Identifier COPY_ICON = Identifier.of(
            DMLS.MOD_ID.toLowerCase(), "textures/gui/icon/copy.png");
    private static final String MASK = "####-####";
    private static final int ICON_SIZE = 12;

    private final Runnable onReveal;
    private final Runnable onCopy;
    private String code;
    private boolean revealed;

    public LinkCodeWidget(int x, int y, int width, int height, String code, boolean revealed,
                          Runnable onReveal, Runnable onCopy) {
        super(x, y, width, height, Text.literal(revealed ? code : MASK));
        this.code = code;
        this.revealed = revealed;
        this.onReveal = onReveal;
        this.onCopy = onCopy;
    }

    public void setCode(String code) {
        this.code = code;
        this.revealed = false;
        setMessage(Text.literal(MASK));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int copyX = copyButtonX();
        context.fill(getX(), getY(), getRight(), getBottom(), DMLSMenuScreen.PANEL_BACKGROUND_COLOR);
        if (isHovered()) {
            int hoverLeft = mouseX >= copyX ? copyX : getX();
            int hoverRight = mouseX >= copyX ? getRight() : copyX;
            context.fill(hoverLeft + 1, getY() + 1, hoverRight - 1, getBottom() - 1, 0x504A4A4A);
        }
        context.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(),
                DMLSMenuScreen.PANEL_BORDER_COLOR);
        context.fill(copyX, getY() + 1, copyX + 1, getBottom() - 1,
                DMLSMenuScreen.PANEL_BORDER_COLOR);

        MinecraftClient client = MinecraftClient.getInstance();
        int textCenterX = getX() + (copyX - getX()) / 2;
        int textY = getY() + (getHeight() - client.textRenderer.fontHeight) / 2 + 1;
        context.drawCenteredTextWithShadow(client.textRenderer, revealed ? code : MASK,
                textCenterX, textY, 0xFFFFFFFF);

        int iconX = copyX + (getHeight() - ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COPY_ICON, iconX, iconY,
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        if (click.x() >= copyButtonX()) {
            copyCode();
        } else {
            revealCode();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!isFocused()) return false;
        if (input.isCopy()) {
            copyCode();
            return true;
        }
        if (input.isEnterOrSpace()) {
            if (revealed) copyCode();
            else revealCode();
            return true;
        }
        return super.keyPressed(input);
    }

    private int copyButtonX() {
        return getRight() - getHeight();
    }

    private void revealCode() {
        if (!revealed) {
            revealed = true;
            setMessage(Text.literal(code));
            onReveal.run();
        }
    }

    private void copyCode() {
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        onCopy.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
        builder.put(NarrationPart.USAGE, Text.translatable(revealed
                ? "dmls.option.discord_link.code.usage_revealed"
                : "dmls.option.discord_link.code.usage_hidden"));
    }
}
