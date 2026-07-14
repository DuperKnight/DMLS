package com.duperknight.client.gui;

import com.duperknight.client.modules.DMLSModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** The registry-driven DMLS module picker. */
public final class DMLSHomeScreen extends DMLSMenuScreen {
    private static final int COLUMNS = 3;
    private static final int CARD_GAP = scaled(18);
    private static final int CARD_MARGIN = scaled(24);
    private static final int MIN_CARD_SIZE = scaled(70);
    private static final int MAX_CARD_SIZE = scaled(120);

    private final List<DMLSModule> registeredModules;
    private List<DMLSModule> visibleModules = List.of();
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;

    public DMLSHomeScreen(List<DMLSModule> registeredModules) {
        this(registeredModules, null);
    }

    public DMLSHomeScreen(List<DMLSModule> registeredModules, Screen parent) {
        super(Text.translatable("dmls.title.home"), parent);
        this.registeredModules = List.copyOf(registeredModules);
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.options"), button -> client.setScreen(new DMLSOptionsScreen(this)))
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.exit"), button -> close())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        visibleModules = registeredModules.stream().filter(DMLSModule::isAvailableToSelectedRank).toList();
        GridLayout layout = layoutFor(visibleModules.size());
        scrollOffset = Math.min(scrollOffset, maxScroll);

        context.enableScissor(layout.viewportX(), layout.viewportY(), layout.viewportRight(), layout.viewportBottom());
        for (int index = 0; index < visibleModules.size(); index++) {
            renderCard(context, layout, index, visibleModules.get(index), mouseX, mouseY);
        }
        context.disableScissor();

        if (visibleModules.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.message.no_modules"),
                    width / 2, layout.viewportY() + layout.viewportHeight() / 2 - 4, 0xFFAAAAAA);
        }
        if (layout.scrollable()) {
            renderScrollbar(context, layout);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCard(DrawContext context, GridLayout layout, int index, DMLSModule module, int mouseX, int mouseY) {
        int cardX = layout.cardX(index % COLUMNS);
        int cardY = layout.contentTop() + (index / COLUMNS) * (layout.cardSize() + CARD_GAP) - scrollOffset;
        boolean hovered = mouseX >= cardX && mouseX < cardX + layout.cardSize()
                && mouseY >= cardY && mouseY < cardY + layout.cardSize()
                && mouseY >= layout.viewportY() && mouseY < layout.viewportBottom();
        context.fill(cardX, cardY, cardX + layout.cardSize(), cardY + layout.cardSize(), hovered ? 0xB8202020 : 0xA8000000);
        context.drawStrokedRectangle(cardX, cardY, layout.cardSize(), layout.cardSize(), hovered ? 0xFFFFFFFF : 0xFFAAAAAA);

        ItemStack icon = module.icon();
        float iconScale = 1.5F * UI_SCALE;
        int iconY = cardY + layout.cardSize() / 2 - scaled(31);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cardX + layout.cardSize() / 2.0F - 8 * iconScale, iconY);
        context.getMatrices().scale(iconScale, iconScale);
        context.drawItem(icon, 0, 0);
        context.getMatrices().popMatrix();

        float labelScale = 1.25F * UI_SCALE;
        List<OrderedText> lines = textRenderer.wrapLines(module.displayName(), (int) ((layout.cardSize() - scaled(14)) / labelScale));
        int labelY = iconY + scaled(35);
        for (OrderedText line : lines) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(cardX + layout.cardSize() / 2.0F, labelY);
            context.getMatrices().scale(labelScale, labelScale);
            context.drawCenteredTextWithShadow(textRenderer, line, 0, 0, 0xFFFFFFFF);
            context.getMatrices().popMatrix();
            labelY += scaled(12);
        }
    }

    private void renderScrollbar(DrawContext context, GridLayout layout) {
        int trackX = layout.viewportRight() + scaled(7);
        int thumbHeight = scrollbarThumbHeight(layout);
        int thumbY = layout.viewportY() + scrollbarThumbOffset(layout, thumbHeight);
        renderVanillaScrollbar(context, trackX, layout.viewportY(), layout.viewportHeight(), thumbY, thumbHeight);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        GridLayout layout = layoutFor(visibleModules.size());
        if (layout.scrollable() && isOverScrollbar(layout, click.x(), click.y())) {
            draggingScrollbar = true;
            updateScrollFromMouse(layout, click.y());
            return true;
        }

        if (click.button() == 0 && click.y() >= layout.viewportY() && click.y() < layout.viewportBottom()) {
            for (int index = 0; index < visibleModules.size(); index++) {
                int cardX = layout.cardX(index % COLUMNS);
                int cardY = layout.contentTop() + (index / COLUMNS) * (layout.cardSize() + CARD_GAP) - scrollOffset;
                if (click.x() >= cardX && click.x() < cardX + layout.cardSize()
                        && click.y() >= cardY && click.y() < cardY + layout.cardSize()) {
                    visibleModules.get(index).openScreen(client, this);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingScrollbar = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            GridLayout layout = layoutFor(visibleModules.size());
            int thumbHeight = scrollbarThumbHeight(layout);
            int track = Math.max(1, layout.viewportHeight() - thumbHeight);
            scrollOffset = Math.clamp(scrollOffset + (int) Math.round(deltaY * maxScroll / track), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        GridLayout layout = layoutFor(visibleModules.size());
        if (layout.scrollable() && mouseX >= layout.panelX() && mouseX < layout.panelX() + layout.panelWidth()
                && mouseY >= layout.viewportY() && mouseY < layout.viewportBottom()) {
            scrollOffset = Math.clamp(scrollOffset - (int) (verticalAmount * scaled(24)), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private GridLayout layoutFor(int moduleCount) {
        int panelWidth = Math.clamp(width - scaled(32), scaled(300), scaled(470));
        int panelX = (width - panelWidth) / 2;
        int viewportY = HEADER_HEIGHT;
        int viewportHeight = Math.max(scaled(80), height - FOOTER_TOP_OFFSET - viewportY);
        int cardAreaWidth = panelWidth - CARD_MARGIN * 2;
        int cardSize = Math.clamp((cardAreaWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS, MIN_CARD_SIZE, MAX_CARD_SIZE);
        int rows = (moduleCount + COLUMNS - 1) / COLUMNS;
        int contentHeight = rows == 0 ? 0
                : CARD_MARGIN * 2 + rows * cardSize + Math.max(0, rows - 1) * CARD_GAP;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        boolean scrollable = maxScroll > 0;
        int viewportWidth = panelWidth - CARD_MARGIN * 2 - (scrollable ? scaled(14) : 0);
        cardSize = Math.clamp(cardSize, MIN_CARD_SIZE, (viewportWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS);
        contentHeight = rows == 0 ? 0
                : CARD_MARGIN * 2 + rows * cardSize + Math.max(0, rows - 1) * CARD_GAP;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll == 0) {
            scrollable = false;
            viewportWidth = panelWidth - CARD_MARGIN * 2;
            cardSize = Math.clamp((viewportWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS, MIN_CARD_SIZE, MAX_CARD_SIZE);
        }
        return new GridLayout(panelX, panelWidth, panelX + CARD_MARGIN, viewportY, viewportWidth, viewportHeight, cardSize, scrollable);
    }

    private int scrollbarThumbHeight(GridLayout layout) {
        int contentHeight = layout.viewportHeight() + maxScroll;
        return scrollbarThumbHeight(layout.viewportHeight(), contentHeight);
    }

    private int scrollbarThumbOffset(GridLayout layout, int thumbHeight) {
        int track = layout.viewportHeight() - thumbHeight;
        return maxScroll == 0 ? 0 : scrollOffset * track / maxScroll;
    }

    private boolean isOverScrollbar(GridLayout layout, double x, double y) {
        int scrollbarX = layout.viewportRight() + scaled(7);
        return x >= scrollbarX && x <= scrollbarX + SCROLLBAR_WIDTH
                && y >= layout.viewportY() && y < layout.viewportBottom();
    }

    private void updateScrollFromMouse(GridLayout layout, double mouseY) {
        int thumbHeight = scrollbarThumbHeight(layout);
        int track = Math.max(1, layout.viewportHeight() - thumbHeight);
        double relative = Math.clamp((int) (mouseY - layout.viewportY() - thumbHeight / 2.0), 0, track);
        scrollOffset = Math.clamp((int) Math.round(relative * maxScroll / track), 0, maxScroll);
    }

    private record GridLayout(int panelX, int panelWidth, int viewportX, int viewportY, int viewportWidth,
                              int viewportHeight, int cardSize, boolean scrollable) {
        int viewportRight() {
            return viewportX + viewportWidth;
        }

        int viewportBottom() {
            return viewportY + viewportHeight;
        }

        int contentTop() {
            return viewportY + CARD_MARGIN;
        }

        int cardX(int column) {
            int gridWidth = cardSize * COLUMNS + CARD_GAP * (COLUMNS - 1);
            return viewportX + (viewportWidth - gridWidth) / 2 + column * (cardSize + CARD_GAP);
        }
    }

}
