package com.duperknight.client.gui;

import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.gui.modules.RulebookScreen;
import com.duperknight.client.modules.ModuleCategory;
import com.duperknight.client.moderation.ModerationScreen;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** The registry-driven DMLS module picker. */
public final class DMLSHomeScreen extends DMLSMenuScreen {
    private static final int COLUMNS = 3;
    private static final int CARD_GAP = scaled(18);
    private static final int CARD_MARGIN = scaled(24);
    private static final int MIN_CARD_SIZE = scaled(70);
    private static final int MAX_CARD_SIZE = scaled(120);
    private static final int CATEGORY_HEADER_HEIGHT = scaled(22);
    private static final int CATEGORY_CARD_GAP = scaled(8);
    private static final int CATEGORY_GAP = scaled(12);
    private static final int PROMOTION_ANNOUNCEMENT_HEIGHT = scaled(78);
    private static final int WELCOME_TOP_PADDING = scaled(6);
    private static final int WELCOME_ANNOUNCEMENT_HEIGHT = scaled(102) + WELCOME_TOP_PADDING;

    private final List<DMLSModule> registeredModules;
    private final RankAnnouncement announcement;
    private final EnumSet<ModuleCategory> collapsedCategories = EnumSet.noneOf(ModuleCategory.class);
    private List<DMLSModule> visibleModules = List.of();
    private List<CategoryGroup> visibleCategories = List.of();
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private boolean accessAtInit;

    public DMLSHomeScreen(List<DMLSModule> registeredModules) {
        this(registeredModules, null);
    }

    public DMLSHomeScreen(List<DMLSModule> registeredModules, Screen parent) {
        this(registeredModules, parent, null);
    }

    private DMLSHomeScreen(List<DMLSModule> registeredModules, Screen parent, RankAnnouncement announcement) {
        super(Text.translatable("dmls.title.home"), parent);
        this.registeredModules = List.copyOf(registeredModules);
        this.announcement = announcement;
    }

    public static DMLSHomeScreen welcome(List<DMLSModule> registeredModules, StaffRank currentRank) {
        return new DMLSHomeScreen(registeredModules, null,
                new RankAnnouncement(AnnouncementType.WELCOME, StaffRank.NONE, currentRank));
    }

    public static DMLSHomeScreen promotion(
            List<DMLSModule> registeredModules,
            StaffRank previousRank,
            StaffRank currentRank
    ) {
        return new DMLSHomeScreen(registeredModules, null,
                new RankAnnouncement(AnnouncementType.PROMOTION, previousRank, currentRank));
    }

    @Override
    protected void init() {
        accessAtInit = DMLSConfig.hasRecognizedStaffRank();
        if (!accessAtInit) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.exit"), button -> close())
                    .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
            return;
        }
        int gap = scaled(8);
        int buttonWidth = Math.min(scaled(135), (width - scaled(32) - gap * 3) / 4);
        int groupWidth = buttonWidth * 4 + gap * 3;
        int firstX = (width - groupWidth) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.open"),
                        button -> client.setScreen(new ModerationScreen(this)))
                .dimensions(firstX, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.rulebook"),
                        button -> client.setScreen(new RulebookScreen(this, null)))
                .dimensions(firstX + buttonWidth + gap, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.options"),
                        button -> client.setScreen(new DMLSOptionsScreen(this)))
                .dimensions(firstX + (buttonWidth + gap) * 2, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.exit"), button -> close())
                .dimensions(firstX + (buttonWidth + gap) * 3, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (accessAtInit != DMLSConfig.hasRecognizedStaffRank()) {
            clearChildren();
            init();
        }
        renderMenuBackground(context);
        visibleModules = registeredModules.stream().filter(DMLSModule::isAvailableToDetectedRank).toList();
        visibleCategories = categoryGroups(visibleModules);
        if (announcement != null) {
            renderRankAnnouncement(context);
        }
        GridLayout layout = layoutFor(visibleCategories);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        context.enableScissor(layout.viewportX(), layout.viewportY(), layout.viewportRight(), layout.viewportBottom());
        for (CategoryLayout categoryLayout : categoryLayouts(layout)) {
            renderCategoryHeader(context, layout, categoryLayout, mouseX, mouseY);
            if (collapsedCategories.contains(categoryLayout.group().category())) continue;
            for (int index = 0; index < categoryLayout.group().modules().size(); index++) {
                int cardX = layout.cardX(index % COLUMNS);
                int cardY = categoryLayout.cardsTop() + (index / COLUMNS) * (layout.cardSize() + CARD_GAP);
                renderCard(context, layout, cardX, cardY, categoryLayout.group().modules().get(index), mouseX, mouseY);
            }
        }
        context.disableScissor();

        if (visibleModules.isEmpty()) {
            Text message = DMLSConfig.hasRecognizedStaffRank()
                    ? Text.translatable("dmls.message.no_modules")
                    : Text.translatable("dmls.message.staff_required");
            context.drawCenteredTextWithShadow(textRenderer, message,
                    width / 2, layout.viewportY() + layout.viewportHeight() / 2 - 4, 0xFFAAAAAA);
        }
        if (layout.scrollable()) {
            renderScrollbar(context, layout);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCard(DrawContext context, GridLayout layout, int cardX, int cardY,
                            DMLSModule module, int mouseX, int mouseY) {
        boolean hovered = mouseX >= cardX && mouseX < cardX + layout.cardSize()
                && mouseY >= cardY && mouseY < cardY + layout.cardSize()
                && mouseY >= layout.viewportY() && mouseY < layout.viewportBottom();
        boolean newlyUnlocked = isNewlyUnlocked(module);
        int backgroundColor = newlyUnlocked
                ? (hovered ? 0xB82A402A : 0xA8122812)
                : (hovered ? 0xB8202020 : 0xA8000000);
        int borderColor = hovered ? 0xFFFFFFFF : newlyUnlocked ? 0xFF55FF55 : 0xFFAAAAAA;
        context.fill(cardX, cardY, cardX + layout.cardSize(), cardY + layout.cardSize(), backgroundColor);
        context.drawStrokedRectangle(cardX, cardY, layout.cardSize(), layout.cardSize(), borderColor);

        if (newlyUnlocked) {
            Text badge = Text.translatable("dmls.promotion.new");
            int badgeWidth = textRenderer.getWidth(badge) + scaled(8);
            int badgeX = cardX + layout.cardSize() - badgeWidth - scaled(4);
            int badgeY = cardY + scaled(4);
            context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + textRenderer.fontHeight + scaled(4), 0xD0206020);
            context.drawCenteredTextWithShadow(textRenderer, badge,
                    badgeX + badgeWidth / 2, badgeY + scaled(2), 0xFFFFFFFF);
        }

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

    private void renderCategoryHeader(DrawContext context, GridLayout layout, CategoryLayout categoryLayout,
                                      int mouseX, int mouseY) {
        int headerY = categoryLayout.headerY();
        boolean hovered = mouseX >= layout.viewportX() && mouseX < layout.viewportRight()
                && mouseY >= headerY && mouseY < headerY + CATEGORY_HEADER_HEIGHT
                && mouseY >= layout.viewportY() && mouseY < layout.viewportBottom();
        if (hovered) {
            context.fill(layout.viewportX(), headerY, layout.viewportRight(), headerY + CATEGORY_HEADER_HEIGHT,
                    0x50383838);
            context.setCursor(StandardCursors.POINTING_HAND);
        }

        Text label = categoryLayout.group().category().displayName();
        int centerX = layout.viewportX() + layout.viewportWidth() / 2;
        int centerY = headerY + CATEGORY_HEADER_HEIGHT / 2;
        int halfLabelWidth = textRenderer.getWidth(label) / 2;
        int linePadding = scaled(8);
        int lineLeft = layout.viewportX() + scaled(20);
        int leftEnd = centerX - halfLabelWidth - linePadding;
        int rightStart = centerX + halfLabelWidth + linePadding;
        if (leftEnd > lineLeft) {
            context.fill(lineLeft, centerY, leftEnd, centerY + 1, PANEL_BORDER_COLOR);
        }
        if (layout.viewportRight() - scaled(4) > rightStart) {
            context.fill(rightStart, centerY, layout.viewportRight() - scaled(4), centerY + 1, PANEL_BORDER_COLOR);
        }
        drawCategoryArrow(context, layout.viewportX() + scaled(9), centerY,
                !collapsedCategories.contains(categoryLayout.group().category()));
        context.drawCenteredTextWithShadow(textRenderer, label, centerX,
                headerY + (CATEGORY_HEADER_HEIGHT - textRenderer.fontHeight) / 2, 0xFFFFFFFF);
    }

    private static void drawCategoryArrow(DrawContext context, int centerX, int centerY, boolean expanded) {
        int color = 0xFFE0E0E0;
        if (expanded) {
            context.fill(centerX - 3, centerY + 1, centerX + 4, centerY + 2, color);
            context.fill(centerX - 2, centerY, centerX + 3, centerY + 1, color);
            context.fill(centerX - 1, centerY - 1, centerX + 2, centerY, color);
        } else {
            context.fill(centerX - 3, centerY - 1, centerX + 4, centerY, color);
            context.fill(centerX - 2, centerY, centerX + 3, centerY + 1, color);
            context.fill(centerX - 1, centerY + 1, centerX + 2, centerY + 2, color);
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
        GridLayout layout = layoutFor(visibleCategories);
        if (layout.scrollable() && isOverScrollbar(layout, click.x(), click.y())) {
            draggingScrollbar = true;
            updateScrollFromMouse(layout, click.y());
            return true;
        }

        if (click.button() == 0 && click.y() >= layout.viewportY() && click.y() < layout.viewportBottom()) {
            for (CategoryLayout categoryLayout : categoryLayouts(layout)) {
                if (click.x() >= layout.viewportX() && click.x() < layout.viewportRight()
                        && click.y() >= categoryLayout.headerY()
                        && click.y() < categoryLayout.headerY() + CATEGORY_HEADER_HEIGHT) {
                    ModuleCategory category = categoryLayout.group().category();
                    if (!collapsedCategories.remove(category)) {
                        collapsedCategories.add(category);
                    }
                    layoutFor(visibleCategories);
                    scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
                    return true;
                }
                if (collapsedCategories.contains(categoryLayout.group().category())) continue;
                for (int index = 0; index < categoryLayout.group().modules().size(); index++) {
                    int cardX = layout.cardX(index % COLUMNS);
                    int cardY = categoryLayout.cardsTop() + (index / COLUMNS) * (layout.cardSize() + CARD_GAP);
                    if (click.x() >= cardX && click.x() < cardX + layout.cardSize()
                            && click.y() >= cardY && click.y() < cardY + layout.cardSize()) {
                        categoryLayout.group().modules().get(index).openScreen(client, this);
                        return true;
                    }
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
            GridLayout layout = layoutFor(visibleCategories);
            int thumbHeight = scrollbarThumbHeight(layout);
            int track = Math.max(1, layout.viewportHeight() - thumbHeight);
            scrollOffset = Math.clamp(scrollOffset + (int) Math.round(deltaY * maxScroll / track), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        GridLayout layout = layoutFor(visibleCategories);
        if (layout.scrollable() && mouseX >= layout.panelX() && mouseX < layout.panelX() + layout.panelWidth()
                && mouseY >= layout.viewportY() && mouseY < layout.viewportBottom()) {
            scrollOffset = Math.clamp(scrollOffset - (int) (verticalAmount * scaled(24)), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private GridLayout layoutFor(List<CategoryGroup> categories) {
        int panelWidth = Math.clamp(width - scaled(32), scaled(300), scaled(470));
        int panelX = (width - panelWidth) / 2;
        int viewportY = HEADER_HEIGHT + announcementHeight();
        int minimumViewportHeight = announcement == null ? scaled(80) : scaled(40);
        int viewportHeight = Math.max(minimumViewportHeight, height - FOOTER_TOP_OFFSET - viewportY);
        int cardMargin = announcement == null ? CARD_MARGIN : scaled(8);
        int minimumCardSize = announcement == null ? MIN_CARD_SIZE : scaled(58);
        int maximumCardSize = announcement == null
                ? MAX_CARD_SIZE
                : Math.max(minimumCardSize, viewportHeight - cardMargin * 2);
        int cardAreaWidth = panelWidth - cardMargin * 2;
        int cardSize = Math.clamp((cardAreaWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS,
                minimumCardSize, maximumCardSize);
        int contentHeight = groupedContentHeight(categories, cardMargin, cardSize);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        boolean scrollable = maxScroll > 0;
        int viewportWidth = panelWidth - cardMargin * 2 - (scrollable ? scaled(14) : 0);
        cardSize = Math.clamp(cardSize, minimumCardSize,
                Math.max(minimumCardSize, (viewportWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS));
        contentHeight = groupedContentHeight(categories, cardMargin, cardSize);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll == 0) {
            scrollable = false;
            viewportWidth = panelWidth - cardMargin * 2;
        }
        return new GridLayout(panelX, panelWidth, panelX + cardMargin, viewportY, viewportWidth, viewportHeight,
                viewportY + cardMargin, cardSize, scrollable);
    }

    private int groupedContentHeight(List<CategoryGroup> categories, int cardMargin, int cardSize) {
        if (categories.isEmpty()) return 0;
        int height = cardMargin * 2;
        for (int index = 0; index < categories.size(); index++) {
            CategoryGroup group = categories.get(index);
            height += CATEGORY_HEADER_HEIGHT;
            if (!collapsedCategories.contains(group.category())) {
                int rows = (group.modules().size() + COLUMNS - 1) / COLUMNS;
                height += CATEGORY_CARD_GAP + rows * cardSize + Math.max(0, rows - 1) * CARD_GAP;
            }
            if (index + 1 < categories.size()) {
                height += CATEGORY_GAP;
            }
        }
        return height;
    }

    private List<CategoryGroup> categoryGroups(List<DMLSModule> modules) {
        List<CategoryGroup> groups = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<DMLSModule> categoryModules = modules.stream()
                    .filter(module -> module.category() == category)
                    .toList();
            if (!categoryModules.isEmpty()) {
                groups.add(new CategoryGroup(category, categoryModules));
            }
        }
        return List.copyOf(groups);
    }

    private List<CategoryLayout> categoryLayouts(GridLayout layout) {
        List<CategoryLayout> layouts = new ArrayList<>();
        int y = layout.contentTop() - scrollOffset;
        for (int index = 0; index < visibleCategories.size(); index++) {
            CategoryGroup group = visibleCategories.get(index);
            int headerY = y;
            y += CATEGORY_HEADER_HEIGHT;
            int cardsTop = y;
            if (!collapsedCategories.contains(group.category())) {
                y += CATEGORY_CARD_GAP;
                cardsTop = y;
                int rows = (group.modules().size() + COLUMNS - 1) / COLUMNS;
                y += rows * layout.cardSize() + Math.max(0, rows - 1) * CARD_GAP;
            }
            layouts.add(new CategoryLayout(group, headerY, cardsTop));
            if (index + 1 < visibleCategories.size()) {
                y += CATEGORY_GAP;
            }
        }
        return layouts;
    }

    private void renderRankAnnouncement(DrawContext context) {
        int topPadding = announcement.type == AnnouncementType.WELCOME ? WELCOME_TOP_PADDING : 0;
        Text heading = Text.translatable(announcement.type == AnnouncementType.WELCOME
                ? "dmls.welcome.title"
                : "dmls.promotion.title");
        context.drawCenteredTextWithShadow(textRenderer, heading,
                width / 2, HEADER_HEIGHT + scaled(4) + topPadding, 0xFFFFFFFF);

        int panelWidth = Math.min(scaled(220), width - scaled(48));
        int panelX = width / 2 - panelWidth / 2;
        int panelY = HEADER_HEIGHT + scaled(24) + topPadding;
        renderPanel(context, panelX, panelY, panelWidth, STANDARD_BUTTON_HEIGHT);
        context.drawCenteredTextWithShadow(textRenderer, announcement.currentRank.displayName(),
                width / 2, panelY + (STANDARD_BUTTON_HEIGHT - textRenderer.fontHeight) / 2 + scaled(2), 0xFFFFFFFF);

        Text summary;
        if (announcement.type == AnnouncementType.WELCOME) {
            summary = Text.translatable("dmls.welcome.summary", visibleModules.size());
        } else {
            int newlyUnlocked = (int) visibleModules.stream().filter(this::isNewlyUnlocked).count();
            summary = Text.translatable(newlyUnlocked == 1
                            ? "dmls.promotion.summary.one"
                            : "dmls.promotion.summary.many",
                    announcement.previousRank.displayName(), newlyUnlocked);
        }
        context.drawCenteredTextWithShadow(textRenderer, summary,
                width / 2, HEADER_HEIGHT + scaled(57) + topPadding, 0xFFDDDDDD);

        if (announcement.type == AnnouncementType.WELCOME) {
            Text hint = Text.translatable("dmls.welcome.open_menu_hint");
            int hintWidth = Math.min(scaled(430), width - scaled(32));
            int hintY = HEADER_HEIGHT + scaled(73) + topPadding;
            for (OrderedText line : textRenderer.wrapLines(hint, hintWidth)) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, hintY, 0xFFAAAAAA);
                hintY += scaled(11);
            }
        }
    }

    private int announcementHeight() {
        if (announcement == null) return 0;
        return announcement.type == AnnouncementType.WELCOME
                ? WELCOME_ANNOUNCEMENT_HEIGHT
                : PROMOTION_ANNOUNCEMENT_HEIGHT;
    }

    private boolean isNewlyUnlocked(DMLSModule module) {
        return announcement != null
                && announcement.type == AnnouncementType.PROMOTION
                && module.isAvailableForStaffRank(announcement.currentRank)
                && !module.isAvailableForStaffRank(announcement.previousRank);
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
                              int viewportHeight, int contentTop, int cardSize, boolean scrollable) {
        int viewportRight() {
            return viewportX + viewportWidth;
        }

        int viewportBottom() {
            return viewportY + viewportHeight;
        }

        int cardX(int column) {
            int gridWidth = cardSize * COLUMNS + CARD_GAP * (COLUMNS - 1);
            return viewportX + (viewportWidth - gridWidth) / 2 + column * (cardSize + CARD_GAP);
        }
    }

    private record CategoryGroup(ModuleCategory category, List<DMLSModule> modules) {
    }

    private record CategoryLayout(CategoryGroup group, int headerY, int cardsTop) {
    }

    private enum AnnouncementType {
        WELCOME,
        PROMOTION
    }

    private record RankAnnouncement(AnnouncementType type, StaffRank previousRank, StaffRank currentRank) {
    }

}
