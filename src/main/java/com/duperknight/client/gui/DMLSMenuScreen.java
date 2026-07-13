package com.duperknight.client.gui;

import com.duperknight.DMLS;
import com.duperknight.client.modules.DMLSModule;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Shared in-game menu chrome used by every DMLS screen. */
abstract class DMLSMenuScreen extends Screen {
    protected static final float UI_SCALE = 0.85F;
    private static final Identifier LOGO = Identifier.of(DMLS.MOD_ID.toLowerCase(), "logo.png");
    private static final Identifier HEADER_SEPARATOR = Identifier.ofVanilla("textures/gui/inworld_header_separator.png");
    private static final Identifier FOOTER_SEPARATOR = Identifier.ofVanilla("textures/gui/inworld_footer_separator.png");
    protected static final Identifier SCROLLER = Identifier.ofVanilla("widget/scroller");
    protected static final Identifier SCROLLER_BACKGROUND = Identifier.ofVanilla("widget/scroller_background");
    protected static final int SCROLLBAR_WIDTH = 6;
    private static final int LOGO_TEXTURE_WIDTH = 2040;
    private static final int LOGO_TEXTURE_HEIGHT = 400;
    protected static final int HEADER_HEIGHT = scaled(80);
    protected static final int FOOTER_TOP_OFFSET = scaled(55);
    protected static final int STANDARD_BUTTON_HEIGHT = 20;
    private static final int PAIRED_BUTTON_MARGIN = scaled(16);
    private static final int MAX_PAIRED_BUTTON_WIDTH = scaled(250);

    protected final Screen parent;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    private int contentViewportTop;
    private int contentViewportBottom;
    private int contentScrollOffset;
    private int maxContentScroll;
    private boolean draggingContentScrollbar;

    protected DMLSMenuScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected void renderMenuBackground(DrawContext context) {
        // Screen.renderBackground has already applied the vanilla blurred in-world background.
        context.fill(0, 0, width, HEADER_HEIGHT, 0x20000000);

        int logoWidth = Math.clamp(width - scaled(32), scaled(160), scaled(205));
        int logoHeight = Math.max(1, logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
        int logoX = (width - logoWidth) / 2;
        int logoY = scaled(22);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F,
                logoWidth, logoHeight, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT,
                LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);

        int footerTop = height - FOOTER_TOP_OFFSET;
        context.fill(0, HEADER_HEIGHT, width, footerTop, 0xA6000000);
        context.fill(0, footerTop, width, height, 0x20000000);

        // Use the same two-layer translucent separators as vanilla in-world lists.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, HEADER_SEPARATOR, 0, HEADER_HEIGHT - 2,
                0.0F, 0.0F, width, 2, 32, 2);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, FOOTER_SEPARATOR, 0, footerTop,
                0.0F, 0.0F, width, 2, 32, 2);
    }

    protected void renderPanel(DrawContext context, int x, int y, int panelWidth, int panelHeight) {
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xC0101010);
        context.drawStrokedRectangle(x, y, panelWidth, panelHeight, 0xFF9A9A9A);
    }

    /** Renders the standard title and description block for a module screen. */
    protected void renderModuleHeader(DrawContext context, DMLSModule module) {
        int descriptionY = HEADER_HEIGHT + scaled(34);
        int descriptionWidth = Math.min(scaled(360), width - scaled(32));
        List<OrderedText> wrappedLines = wrappedDescription(module, descriptionWidth);
        int lineSpacing = scaled(12);
        int descriptionHeight = descriptionHeight(wrappedLines.size());
        context.drawCenteredTextWithShadow(textRenderer, module.displayName(), width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);
        renderPanel(context, width / 2 - descriptionWidth / 2, descriptionY, descriptionWidth, descriptionHeight);

        int lineY = descriptionY + (descriptionHeight - wrappedLines.size() * scaled(10)) / 2;
        for (OrderedText line : wrappedLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, lineY, 0xFFDDDDDD);
            lineY += lineSpacing;
        }
    }

    /** Sets up a naturally spaced module form that can scroll without moving its footer buttons. */
    protected void configureScrollableContent(DMLSModule module, int contentHeight) {
        int descriptionWidth = Math.min(scaled(360), width - scaled(32));
        int descriptionBottom = HEADER_HEIGHT + scaled(34)
                + descriptionHeight(wrappedDescription(module, descriptionWidth).size());
        configureScrollableContent(descriptionBottom + scaled(10), contentHeight);
    }

    /** Configures scrolling for non-module settings screens. */
    protected void configureScrollableContent(int viewportTop, int contentHeight) {
        scrollableWidgets.clear();
        contentViewportTop = viewportTop;
        contentViewportBottom = Math.max(contentViewportTop + 1, height - FOOTER_TOP_OFFSET - scaled(8));
        maxContentScroll = Math.max(0, contentHeight - (contentViewportBottom - contentViewportTop));
        contentScrollOffset = Math.clamp(contentScrollOffset, 0, maxContentScroll);
    }

    protected int contentY(int offset) {
        return contentViewportTop + offset - contentScrollOffset;
    }

    protected <T extends ClickableWidget> T addScrollableChild(T widget, int offset) {
        scrollableWidgets.add(new ScrollableWidget(widget, offset));
        T child = addDrawableChild(widget);
        updateScrollableWidgets();
        return child;
    }

    protected boolean isContentVisible(int y, int elementHeight) {
        return y >= contentViewportTop && y + elementHeight <= contentViewportBottom;
    }

    private List<OrderedText> wrappedDescription(DMLSModule module, int descriptionWidth) {
        return module.description().stream()
                .flatMap(line -> textRenderer.wrapLines(line, descriptionWidth - scaled(16)).stream())
                .toList();
    }

    private int descriptionHeight(int lineCount) {
        return Math.max(scaled(48), lineCount * scaled(12) + scaled(20));
    }

    private void updateScrollableWidgets() {
        for (ScrollableWidget entry : scrollableWidgets) {
            ClickableWidget widget = entry.widget();
            widget.setY(contentY(entry.offset()));
            widget.visible = isContentVisible(widget.getY(), widget.getHeight());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (maxContentScroll > 0) {
            int trackX = contentScrollbarX();
            int viewportHeight = contentViewportBottom - contentViewportTop;
            int thumbHeight = scrollbarThumbHeight(viewportHeight, viewportHeight + maxContentScroll);
            int thumbY = contentViewportTop + contentScrollOffset * (viewportHeight - thumbHeight) / maxContentScroll;
            renderVanillaScrollbar(context, trackX, contentViewportTop, viewportHeight, thumbY, thumbHeight);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxContentScroll > 0 && mouseY >= contentViewportTop && mouseY < contentViewportBottom) {
            contentScrollOffset = Math.clamp(contentScrollOffset - (int) (verticalAmount * scaled(24)), 0, maxContentScroll);
            updateScrollableWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (maxContentScroll > 0 && click.button() == 0
                && click.x() >= contentScrollbarX() && click.x() <= contentScrollbarX() + SCROLLBAR_WIDTH
                && click.y() >= contentViewportTop && click.y() < contentViewportBottom) {
            draggingContentScrollbar = true;
            updateContentScrollFromMouse(click.y());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingContentScrollbar) {
            updateContentScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingContentScrollbar = false;
        return super.mouseReleased(click);
    }

    private int contentScrollbarX() {
        int contentWidth = Math.min(scaled(360), width - scaled(48));
        return width / 2 + contentWidth / 2 + scaled(7);
    }

    private void updateContentScrollFromMouse(double mouseY) {
        int viewportHeight = contentViewportBottom - contentViewportTop;
        int thumbHeight = scrollbarThumbHeight(viewportHeight, viewportHeight + maxContentScroll);
        int track = Math.max(1, viewportHeight - thumbHeight);
        double relative = Math.clamp((int) (mouseY - contentViewportTop - thumbHeight / 2.0), 0, track);
        contentScrollOffset = Math.clamp((int) Math.round(relative * maxContentScroll / track), 0, maxContentScroll);
        updateScrollableWidgets();
    }

    protected static int scrollbarThumbHeight(int viewportHeight, int contentHeight) {
        return Math.clamp(viewportHeight * viewportHeight / Math.max(1, contentHeight),
                32, Math.max(32, viewportHeight - 8));
    }

    protected static void renderVanillaScrollbar(DrawContext context, int x, int y, int height,
                                                  int thumbY, int thumbHeight) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND,
                x, y, SCROLLBAR_WIDTH, height);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SCROLLER,
                x, thumbY, SCROLLBAR_WIDTH, thumbHeight);
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

    protected int footerButtonY() {
        return height - 31;
    }

    protected static int scaled(int value) {
        return Math.round(value * UI_SCALE);
    }

    /** Closes the whole DMLS menu stack and returns directly to gameplay. */
    protected void closeToGame() {
        client.setScreen(null);
    }

    private record ScrollableWidget(ClickableWidget widget, int offset) {
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
