package com.duperknight.client.gui;

import com.duperknight.DMLS;
import com.duperknight.client.modules.DMLSModule;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Shared in-game menu chrome used by every DMLS screen. */
abstract class DMLSMenuScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(DMLS.MOD_ID.toLowerCase(), "logo.png");
    private static final int LOGO_TEXTURE_WIDTH = 2040;
    private static final int LOGO_TEXTURE_HEIGHT = 400;
    protected static final int HEADER_HEIGHT = 80;
    protected static final int FOOTER_TOP_OFFSET = 55;
    private static final int PAIRED_BUTTON_MARGIN = 16;
    private static final int MAX_PAIRED_BUTTON_WIDTH = 250;

    protected final Screen parent;

    protected DMLSMenuScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected void renderMenuBackground(DrawContext context) {
        // Screen.renderBackground has already applied the vanilla blurred in-world background.
        context.fill(0, 0, width, HEADER_HEIGHT, 0x20000000);
        context.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT + 1, 0xFF8A8A8A);

        int logoWidth = Math.clamp(width - 32, 160, 205);
        int logoHeight = Math.max(1, logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
        int logoX = (width - logoWidth) / 2;
        int logoY = 22;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F,
                logoWidth, logoHeight, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT,
                LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);

        context.fill(0, HEADER_HEIGHT + 1, width, height - FOOTER_TOP_OFFSET, 0xA6000000);
        context.fill(0, height - FOOTER_TOP_OFFSET + 1, width, height, 0x20000000);
        context.fill(0, height - FOOTER_TOP_OFFSET, width, height - FOOTER_TOP_OFFSET + 2, 0xFF8A8A8A);
    }

    protected void renderPanel(DrawContext context, int x, int y, int panelWidth, int panelHeight) {
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xC0101010);
        context.drawStrokedRectangle(x, y, panelWidth, panelHeight, 0xFF9A9A9A);
    }

    /** Renders the standard title and description block for a module screen. */
    protected void renderModuleHeader(DrawContext context, DMLSModule module) {
        int descriptionY = HEADER_HEIGHT + 34;
        int descriptionWidth = Math.min(360, width - 32);
        List<OrderedText> wrappedLines = module.description().stream()
                .flatMap(line -> textRenderer.wrapLines(line, descriptionWidth - 16).stream())
                .toList();
        int descriptionHeight = Math.max(48, wrappedLines.size() * 12 + 20);
        context.drawCenteredTextWithShadow(textRenderer, module.displayName(), width / 2, HEADER_HEIGHT + 16, 0xFFFFFFFF);
        renderPanel(context, width / 2 - descriptionWidth / 2, descriptionY, descriptionWidth, descriptionHeight);

        int lineY = descriptionY + (descriptionHeight - wrappedLines.size() * 10) / 2;
        for (OrderedText line : wrappedLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, lineY, 0xFFDDDDDD);
            lineY += 12;
        }
    }

    protected int pairedButtonWidth() {
        return Math.min(MAX_PAIRED_BUTTON_WIDTH, (width - PAIRED_BUTTON_MARGIN * 3) / 2);
    }

    protected int leftPairedButtonX() {
        return width / 2 - pairedButtonWidth() - PAIRED_BUTTON_MARGIN / 2;
    }

    protected int rightPairedButtonX() {
        return width / 2 + PAIRED_BUTTON_MARGIN / 2;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
