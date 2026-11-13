package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.CustomMinis;
import com.duperknight.client.modules.MiniMeHudModule;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.utils.DMLSConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * AdMinis HUD settings: click a card to toggle that mini. Custom minis get Edit / delete buttons on
 * hover, the "All" card toggles every usable mini, and the "+" card makes a new blank custom mini.
 * Chaos Mode / Snowball Fights are text-only option cards. At most two rows are visible; extra rows
 * scroll a row at a time (mouse wheel or scrollbar) while Back stays put.
 */
public final class MiniMeHudScreen extends DMLSMenuScreen {
    private static final String MOD_ID = "dmls";
    /** Where the built-in mini textures live: assets/<MOD_ID>/textures/gui/mini_me/<id>/... */
    private static final String TEXTURE_DIR = "textures/gui/mini_me";

    private static final int CARD_WIDTH = 72;
    private static final int CARD_HEIGHT = 88;
    private static final int MIN_CARD_HEIGHT = 66;
    private static final int CARD_GAP = 8;
    private static final int SPRITE_BOX = 52;
    private static final int BACK_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CORNER_MARGIN = 8;
    private static final int HEADER_Y = 14;
    private static final int HEADER_HEIGHT = 20;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_COLOR = 0x99000000;

    private static final int COLOR_CARD = 0xAA101418;
    private static final int COLOR_CARD_HOVER = 0xCC1E262C;
    private static final int COLOR_ON = 0xFF55FF55;
    private static final int COLOR_OFF = 0xFF555B61;
    private static final int COLOR_HOVER_OFF = 0xFFAAB2B8;

    /** The little Edit / delete buttons that appear on a hovered custom card. */
    private static final int TOOL_SIZE = 12;
    private static final int TOOL_INSET = 3;
    private static final int EDIT_WIDTH = 28;
    private static final int MAX_CUSTOM_MINIS = 12;

    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_INSET = 3;
    private static final int COLOR_SCROLL_TRACK = 0xFF2A2F34;
    private static final int COLOR_SCROLL_THUMB = 0xFF8A9096;
    private static final int MIN_THUMB_HEIGHT = 12;
    private static final int SCROLL_AREA_GAP = 12;
    private static final int MAX_VISIBLE_ROWS = 2;

    static final long HIT_FRAME_MS = 150;
    static final long WALK_FRAME_MS = 180;

    /** One toggleable card: a built-in mini, a custom mini, or a text-only option. */
    private record Card(String id, Component label, boolean sprite, boolean custom,
                        Function<MiniMeHudPreferences, Boolean> read,
                        BiFunction<MiniMeHudPreferences, Boolean, MiniMeHudPreferences> update) {
        /** Built-ins and options are always usable; a custom mini needs at least one image. */
        boolean usable() {
            if (!custom) return true;
            CustomMinis.CustomMini mini = CustomMinis.find(id);
            return mini != null && mini.usable();
        }

        /** The standing image, or null for options and blank custom minis. */
        Identifier idle() {
            if (!sprite) return null;
            if (!custom) {
                return Identifier.fromNamespaceAndPath(MOD_ID,
                        (TEXTURE_DIR + "/%s/%s.png").formatted(id, id));
            }
            CustomMinis.CustomMini mini = CustomMinis.find(id);
            CustomMinis.Sprite sprite = mini == null ? null : mini.idleSprite();
            return sprite == null ? null : sprite.texture();
        }
    }

    private enum Kind { MINI, OPTION, ALL, ADD }

    private record CardRef(Kind kind, Card card) {
        String key() {
            return card == null ? kind.name() : kind.name() + ":" + card.id();
        }
    }

    private static Card mini(String id, Function<MiniMeHudPreferences, Boolean> read,
                             BiFunction<MiniMeHudPreferences, Boolean, MiniMeHudPreferences> update) {
        return new Card(id, Component.translatable("dmls.module.mini_me_hud." + id), true, false, read, update);
    }

    private static Card option(String id, Function<MiniMeHudPreferences, Boolean> read,
                               BiFunction<MiniMeHudPreferences, Boolean, MiniMeHudPreferences> update) {
        return new Card(id, Component.translatable("dmls.module.mini_me_hud." + id), false, false, read, update);
    }

    private static final List<Card> BUILT_IN = List.of(
            mini("dupey", MiniMeHudPreferences::dupeyHud, MiniMeHudPreferences::withDupeyHud),
            mini("siaffy", MiniMeHudPreferences::siaffyHud, MiniMeHudPreferences::withSiaffyHud),
            mini("beany", MiniMeHudPreferences::beanyHud, MiniMeHudPreferences::withBeanyHud),
            mini("morvy", MiniMeHudPreferences::morvyHud, MiniMeHudPreferences::withMorvyHud),
            mini("biggy", MiniMeHudPreferences::biggyHud, MiniMeHudPreferences::withBiggyHud),
            mini("serpy", MiniMeHudPreferences::serpyHud, MiniMeHudPreferences::withSerpyHud),
            mini("doggy", MiniMeHudPreferences::doggyHud, MiniMeHudPreferences::withDoggyHud),
            mini("confy", MiniMeHudPreferences::confyHud, MiniMeHudPreferences::withConfyHud));

    private static final List<Card> OPTIONS = List.of(
            option("chaos_mode", MiniMeHudPreferences::chaosMode, MiniMeHudPreferences::withChaosMode),
            option("snowball_fights", MiniMeHudPreferences::snowballFights, MiniMeHudPreferences::withSnowballFights));

    private record Frames(List<Identifier> walking, List<Identifier> hitting) {}

    private static final Map<String, Frames> FRAMES = new HashMap<>();
    private static final Map<Identifier, int[]> SPRITE_SIZES = new HashMap<>();

    private final Screen returnScreen;
    private Component saveStatus = Component.empty();
    private List<CardRef> cards = List.of();
    private int scrollRow;
    /** Set after adding a mini so the new card is scrolled into view. */
    private boolean scrollToEnd;
    private final Map<String, Long> hoverStart = new HashMap<>();

    public MiniMeHudScreen(Screen parent, MiniMeHudModule module) {
        super(Component.translatable("dmls.module.mini_me_hud.name"), parent);
        this.returnScreen = parent;
    }

    private void refreshCards() {
        List<CardRef> list = new ArrayList<>();
        for (Card card : BUILT_IN) {
            list.add(new CardRef(Kind.MINI, card));
        }
        List<CustomMinis.CustomMini> customs = CustomMinis.all();
        for (CustomMinis.CustomMini custom : customs) {
            list.add(new CardRef(Kind.MINI, customCard(custom)));
        }
        for (Card option : OPTIONS) {
            list.add(new CardRef(Kind.OPTION, option));
        }
        list.add(new CardRef(Kind.ALL, null));
        if (customs.size() < MAX_CUSTOM_MINIS) {
            list.add(new CardRef(Kind.ADD, null));
        }
        cards = list;
    }

    private Card customCard(CustomMinis.CustomMini mini) {
        String id = mini.id();
        Component label = Component.literal(font.plainSubstrByWidth(mini.name(), CARD_WIDTH - 8));
        return new Card(id, label, true, true, p -> p.customHud(id), (p, enabled) -> p.withCustomHud(id, enabled));
    }

    // ---------------------------------------------------------------- layout and scrolling

    private int cardsPerRow() {
        return Math.max(1, Math.min(cards.size(), (width - 24 + CARD_GAP) / (CARD_WIDTH + CARD_GAP)));
    }

    private int cardsTop() {
        return HEADER_Y + HEADER_HEIGHT + 12;
    }

    private int totalRows() {
        return (cards.size() + cardsPerRow() - 1) / cardsPerRow();
    }

    private int cappedRows() {
        return Math.min(totalRows(), MAX_VISIBLE_ROWS);
    }

    private int footerY() {
        return height - CORNER_MARGIN - BUTTON_HEIGHT;
    }

    private int cardHeight() {
        int rows = Math.max(1, cappedRows());
        int space = footerY() - SCROLL_AREA_GAP - cardsTop();
        int fitted = (space - (rows - 1) * CARD_GAP) / rows;
        return Math.clamp(fitted, MIN_CARD_HEIGHT, CARD_HEIGHT);
    }

    private int rowStride() {
        return cardHeight() + CARD_GAP;
    }

    private int spriteBox() {
        return Math.max(24, SPRITE_BOX - (CARD_HEIGHT - cardHeight()));
    }

    private int naturalBackY() {
        return cardsTop() + cappedRows() * rowStride() - CARD_GAP + 16;
    }

    private boolean pinnedToFooter() {
        return naturalBackY() > footerY();
    }

    private int backY() {
        return pinnedToFooter() ? footerY() : naturalBackY();
    }

    private int visibleRows() {
        if (!pinnedToFooter()) return cappedRows();
        int fit = Math.max(1, (backY() - SCROLL_AREA_GAP - cardsTop() + CARD_GAP) / rowStride());
        return Math.min(fit, cappedRows());
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private void clampScroll() {
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
    }

    private boolean cardVisible(int index) {
        int row = index / cardsPerRow();
        return row >= scrollRow && row < scrollRow + visibleRows();
    }

    private int cardX(int index) {
        int perRow = cardsPerRow();
        int inThisRow = Math.min(perRow, cards.size() - (index / perRow) * perRow);
        int rowWidth = inThisRow * CARD_WIDTH + (inThisRow - 1) * CARD_GAP;
        return (width - rowWidth) / 2 + (index % perRow) * (CARD_WIDTH + CARD_GAP);
    }

    private int cardY(int index) {
        return cardsTop() + (index / cardsPerRow() - scrollRow) * rowStride();
    }

    private int panelContentWidth() {
        int perRow = cardsPerRow();
        return Math.max(perRow * CARD_WIDTH + (perRow - 1) * CARD_GAP, BACK_WIDTH);
    }

    private int panelLeft() {
        return (width - panelContentWidth()) / 2 - PANEL_PADDING;
    }

    private int panelRight() {
        return (width + panelContentWidth()) / 2 + PANEL_PADDING;
    }

    private int scrollbarX() {
        return panelRight() - SCROLLBAR_INSET - SCROLLBAR_WIDTH;
    }

    private int scrollbarHeight() {
        return visibleRows() * rowStride() - CARD_GAP;
    }

    private int thumbHeight() {
        int track = scrollbarHeight();
        return Math.clamp((long) track * visibleRows() / Math.max(1, totalRows()), MIN_THUMB_HEIGHT, track);
    }

    private int thumbTop() {
        int max = maxScroll();
        if (max <= 0) return cardsTop();
        return cardsTop() + (scrollbarHeight() - thumbHeight()) * scrollRow / max;
    }

    private void drawPanel(GuiGraphicsExtractor context) {
        int top = HEADER_Y - PANEL_PADDING;
        int bottom = backY() + BUTTON_HEIGHT + PANEL_PADDING;
        context.fill(panelLeft(), top, panelRight(), bottom, PANEL_COLOR);
    }

    private void drawScrollbar(GuiGraphicsExtractor context) {
        if (maxScroll() <= 0) return;
        int x = scrollbarX();
        int top = cardsTop();
        context.fill(x, top, x + SCROLLBAR_WIDTH, top + scrollbarHeight(), COLOR_SCROLL_TRACK);
        int thumb = thumbTop();
        context.fill(x, thumb, x + SCROLLBAR_WIDTH, thumb + thumbHeight(), COLOR_SCROLL_THUMB);
    }

    @Override
    protected void init() {
        refreshCards();
        if (scrollToEnd) {
            scrollRow = Integer.MAX_VALUE;
            scrollToEnd = false;
        }
        clampScroll();
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - BACK_WIDTH / 2, backY(), BACK_WIDTH, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scrollBy(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scrollBy(int rows) {
        int next = Math.clamp((long) scrollRow + rows, 0, maxScroll());
        if (next != scrollRow) {
            scrollRow = next;
            hoverStart.clear();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxScroll() > 0 && inside(click.x(), click.y(), scrollbarX() - 2, cardsTop(),
                    SCROLLBAR_WIDTH + 4, scrollbarHeight())) {
                scrollBy(click.y() < thumbTop() ? -1 : click.y() > thumbTop() + thumbHeight() ? 1 : 0);
                return true;
            }
            for (int index = 0; index < cards.size(); index++) {
                if (!cardVisible(index)) continue;
                int cx = cardX(index);
                int cy = cardY(index);
                if (!inside(click.x(), click.y(), cx, cy, CARD_WIDTH, cardHeight())) continue;

                CardRef ref = cards.get(index);
                switch (ref.kind()) {
                    case ALL -> toggleAll();
                    case ADD -> addCustomMini();
                    case OPTION -> toggle(ref.card());
                    case MINI -> clickMini(ref.card(), click.x(), click.y(), cx, cy);
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void clickMini(Card card, double mx, double my, int cx, int cy) {
        if (card.custom()) {
            if (inside(mx, my, cx + TOOL_INSET, cy + TOOL_INSET, TOOL_SIZE, TOOL_SIZE)) {
                confirmDelete(card);
                return;
            }
            if (inside(mx, my, editButtonX(cx), cy + TOOL_INSET, EDIT_WIDTH, TOOL_SIZE) || !card.usable()) {
                // A blank custom mini has nothing to toggle yet, so clicking it opens the editor too.
                minecraft.gui.setScreen(new com.duperknight.client.gui.modules.MiniMeHudEditScreen(this, card.id()));
                return;
            }
        }
        toggle(card);
    }

    private static int editButtonX(int cardX) {
        return cardX + CARD_WIDTH - TOOL_INSET - EDIT_WIDTH;
    }

    private void addCustomMini() {
        String id = CustomMinis.create("Custom " + (CustomMinis.all().size() + 1));
        if (id == null) {
            saveStatus = Component.translatable("dmls.validation.config.save_failed");
            return;
        }
        saveStatus = Component.empty();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        scrollToEnd = true;
        rebuildWidgets();
    }

    private void confirmDelete(Card card) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) deleteCustom(card.id());
            minecraft.gui.setScreen(this);
        }, Component.translatable("dmls.module.mini_me_hud.delete.title"),
                Component.translatable("dmls.module.mini_me_hud.delete.message", card.label())));
    }

    private void deleteCustom(String id) {
        MiniMeHudPreferences previous = DMLSConfig.miniMeHudPreferences();
        CustomMinis.delete(id);
        applyPreferences(previous.withCustomHud(id, false));
    }

    private void toggle(Card card) {
        MiniMeHudPreferences previous = DMLSConfig.miniMeHudPreferences();
        boolean next = !card.read().apply(previous);
        applyPreferences(card.update().apply(previous, next));
    }

    /** If every usable mini is on, turns them all off; otherwise turns them all on. Options are untouched. */
    private void toggleAll() {
        MiniMeHudPreferences previous = DMLSConfig.miniMeHudPreferences();
        boolean next = !allOn(previous);
        MiniMeHudPreferences updated = previous;
        for (Card card : allTargets()) {
            updated = card.update().apply(updated, next);
        }
        applyPreferences(updated);
    }

    private void applyPreferences(MiniMeHudPreferences updated) {
        if (DMLSConfig.setMiniMeHudPreferences(updated)) {
            saveStatus = Component.empty();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } else {
            saveStatus = Component.translatable("dmls.validation.config.save_failed");
        }
    }

    /** The minis the "All" card controls: every mini except blank custom ones. Options are excluded. */
    private List<Card> allTargets() {
        List<Card> targets = new ArrayList<>();
        for (CardRef ref : cards) {
            if (ref.kind() == Kind.MINI && ref.card().usable()) targets.add(ref.card());
        }
        return targets;
    }

    private boolean allOn(MiniMeHudPreferences p) {
        for (Card card : allTargets()) {
            if (!card.read().apply(p)) return false;
        }
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        clampScroll();
        renderMenuBackground(context);
        drawPanel(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, HEADER_Y + (HEADER_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
        drawScrollbar(context);

        MiniMeHudPreferences p = DMLSConfig.miniMeHudPreferences();
        long now = System.currentTimeMillis();
        for (int index = 0; index < cards.size(); index++) {
            CardRef ref = cards.get(index);
            String key = ref.key();
            if (!cardVisible(index)) {
                hoverStart.remove(key);
                continue;
            }
            int cx = cardX(index);
            int cy = cardY(index);
            boolean hovered = inside(mouseX, mouseY, cx, cy, CARD_WIDTH, cardHeight());
            if (hovered) hoverStart.putIfAbsent(key, now);
            else hoverStart.remove(key);

            switch (ref.kind()) {
                case ALL -> drawAllCard(context, cx, cy, p, hovered);
                case ADD -> drawAddCard(context, cx, cy, hovered);
                case OPTION -> drawOptionCard(context, ref.card(), cx, cy, ref.card().read().apply(p), hovered);
                case MINI -> {
                    Card card = ref.card();
                    boolean on = card.usable() && card.read().apply(p);
                    long elapsed = hovered ? now - hoverStart.get(key) : -1L;
                    drawMiniCard(context, card, cx, cy, on, hovered, elapsed, mouseX, mouseY);
                }
            }
        }

        if (!saveStatus.getString().isEmpty()) {
            context.centeredText(font, saveStatus, width / 2, height - 20, 0xFFFF5555);
        }
    }

    private void drawCardFrame(GuiGraphicsExtractor context, int x, int y, boolean on, boolean hovered) {
        int border = on ? COLOR_ON : hovered ? COLOR_HOVER_OFF : COLOR_OFF;
        context.fill(x, y, x + CARD_WIDTH, y + cardHeight(), border);
        context.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + cardHeight() - 1,
                hovered ? COLOR_CARD_HOVER : COLOR_CARD);
    }

    /** ON / OFF, or "Empty" for a blank custom mini. */
    private void drawStatus(GuiGraphicsExtractor context, int x, int y, boolean on, boolean blank) {
        Component status = blank ? Component.translatable("dmls.module.mini_me_hud.empty")
                : Component.translatable(on ? "dmls.option.on" : "dmls.option.off");
        int color = blank ? 0xFF888888 : on ? COLOR_ON : 0xFFFF6666;
        context.centeredText(font, status, x + CARD_WIDTH / 2, y + cardHeight() - 14, color);
    }

    private void drawCardFooter(GuiGraphicsExtractor context, Component label, int x, int y,
                                boolean on, boolean blank) {
        Component shown = Component.literal(font.plainSubstrByWidth(label.getString(), CARD_WIDTH - 8));
        context.centeredText(font, shown, x + CARD_WIDTH / 2, y + cardHeight() - 26,
                on ? 0xFFFFFFFF : 0xFFAAAAAA);
        drawStatus(context, x, y, on, blank);
    }

    private void drawMiniCard(GuiGraphicsExtractor context, Card card, int x, int y,
                              boolean on, boolean hovered, long elapsed, int mouseX, int mouseY) {
        drawCardFrame(context, x, y, on, hovered);

        int boxX = x + (CARD_WIDTH - spriteBox()) / 2;
        int boxY = y + 8;

        // Every frame is resized to fit the box using its own dimensions, so a frame drawn on a
        // smaller (or larger) canvas than the idle sprite still fills the box instead of looking tiny.
        Identifier current = currentTexture(card, elapsed);
        int[] size = spriteSize(current);

        if (size[0] > 0 && size[1] > 0) {
            float scale = Math.min(spriteBox() / (float) size[0], spriteBox() / (float) size[1]);
            int w = Math.max(1, Math.round(size[0] * scale));
            int h = Math.max(1, Math.round(size[1] * scale));
            int left = boxX + (spriteBox() - w) / 2;
            int top = boxY + (spriteBox() - h);
            context.blit(current, left, top, left + w, top + h, 0.0F, 1.0F, 0.0F, 1.0F);
            if (!on) context.fill(left, top, left + w, top + h, 0x99101418);
        } else {
            // No texture yet (a blank custom mini): placeholder so the card is still clickable.
            context.fill(boxX, boxY, boxX + spriteBox(), boxY + spriteBox(), 0xFF2A2F34);
            context.centeredText(font, Component.literal("?"), boxX + spriteBox() / 2,
                    boxY + spriteBox() / 2 - font.lineHeight / 2, 0xFF888888);
        }

        drawCardFooter(context, card.label(), x, y, on, !card.usable());

        if (card.custom() && hovered) {
            drawToolButtons(context, x, y, mouseX, mouseY);
        }
    }

    /** Delete (top-left) and Edit (top-right) buttons, shown only while a custom card is hovered. */
    private void drawToolButtons(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        int textY = y + TOOL_INSET + (TOOL_SIZE - font.lineHeight) / 2 + 1;

        int deleteX = x + TOOL_INSET;
        boolean overDelete = inside(mouseX, mouseY, deleteX, y + TOOL_INSET, TOOL_SIZE, TOOL_SIZE);
        context.fill(deleteX, y + TOOL_INSET, deleteX + TOOL_SIZE, y + TOOL_INSET + TOOL_SIZE,
                overDelete ? 0xFFCC4444 : 0xFF7A2E2E);
        context.centeredText(font, Component.literal("x"), deleteX + TOOL_SIZE / 2, textY, 0xFFFFFFFF);

        int editX = editButtonX(x);
        boolean overEdit = inside(mouseX, mouseY, editX, y + TOOL_INSET, EDIT_WIDTH, TOOL_SIZE);
        context.fill(editX, y + TOOL_INSET, editX + EDIT_WIDTH, y + TOOL_INSET + TOOL_SIZE,
                overEdit ? 0xFF4C7FB8 : 0xFF2E4C70);
        context.centeredText(font, Component.translatable("dmls.module.mini_me_hud.edit"),
                editX + EDIT_WIDTH / 2, textY, 0xFFFFFFFF);
    }

    /** Text-only card for Chaos Mode / Snowball Fights: wrapped name in the sprite area. */
    private void drawOptionCard(GuiGraphicsExtractor context, Card card, int x, int y,
                                boolean on, boolean hovered) {
        drawCardFrame(context, x, y, on, hovered);

        List<FormattedCharSequence> lines = font.split(card.label(), CARD_WIDTH - 10);
        int blockHeight = lines.size() * font.lineHeight;
        int textY = y + 8 + (spriteBox() - blockHeight) / 2;
        for (FormattedCharSequence line : lines) {
            context.centeredText(font, line, x + CARD_WIDTH / 2, textY, on ? 0xFFFFFFFF : 0xFFAAAAAA);
            textY += font.lineHeight;
        }
        drawStatus(context, x, y, on, false);
    }

    /** The "All" card: a grid of small mini previews (each dimmed if that mini is off). */
    private void drawAllCard(GuiGraphicsExtractor context, int x, int y, MiniMeHudPreferences p, boolean hovered) {
        boolean allOn = allOn(p);
        drawCardFrame(context, x, y, allOn, hovered);

        int boxX = x + (CARD_WIDTH - spriteBox()) / 2;
        int boxY = y + 8;

        List<Card> targets = allTargets();
        int n = targets.size();
        if (n > 0) {
            int cols = (int) Math.ceil(Math.sqrt(n));
            int rows = (n + cols - 1) / cols;
            int cell = spriteBox() / cols;
            int gridTop = boxY + (spriteBox() - rows * cell) / 2;

            for (int i = 0; i < n; i++) {
                Card card = targets.get(i);
                int row = i / cols;
                int inRow = Math.min(cols, n - row * cols);
                int rowLeft = boxX + (spriteBox() - inRow * cell) / 2;
                int cellX = rowLeft + (i % cols) * cell;
                int cellY = gridTop + row * cell;

                Identifier texture = card.idle();
                int[] size = spriteSize(texture);
                if (size[0] <= 0 || size[1] <= 0) continue;

                float scale = Math.min(cell / (float) size[0], cell / (float) size[1]);
                int w = Math.max(1, Math.round(size[0] * scale));
                int h = Math.max(1, Math.round(size[1] * scale));
                int left = cellX + (cell - w) / 2;
                int top = cellY + (cell - h);
                context.blit(texture, left, top, left + w, top + h, 0.0F, 1.0F, 0.0F, 1.0F);
                if (!card.read().apply(p)) context.fill(left, top, left + w, top + h, 0x99101418);
            }
        }
        drawCardFooter(context, Component.translatable("dmls.module.mini_me_hud.all"), x, y, allOn, false);
    }

    /** The "+" card that creates a new blank custom mini. */
    private void drawAddCard(GuiGraphicsExtractor context, int x, int y, boolean hovered) {
        drawCardFrame(context, x, y, false, hovered);

        int centerX = x + CARD_WIDTH / 2;
        int centerY = y + 8 + spriteBox() / 2;
        int color = hovered ? 0xFFFFFFFF : 0xFF8A9096;
        context.fill(centerX - 12, centerY - 2, centerX + 12, centerY + 2, color);
        context.fill(centerX - 2, centerY - 12, centerX + 2, centerY + 12, color);

        context.centeredText(font, Component.translatable("dmls.module.mini_me_hud.add"),
                centerX, y + cardHeight() - 20, color);
    }

    // ---------------------------------------------------------------- animation

    private static Frames frames(Card card) {
        if (card.custom()) {
            CustomMinis.CustomMini mini = CustomMinis.find(card.id());
            if (mini == null) return new Frames(List.of(), List.of());
            return new Frames(textures(mini.walking()), textures(mini.hitting()));
        }
        return FRAMES.computeIfAbsent(card.id(),
                id -> new Frames(discover(id, "walking"), discover(id, "hitting")));
    }

    private static List<Identifier> textures(List<CustomMinis.Sprite> sprites) {
        List<Identifier> out = new ArrayList<>();
        for (CustomMinis.Sprite sprite : sprites) {
            out.add(sprite.texture());
        }
        return out;
    }

    /** Finds numbered frames (id_anim_00.png, _01, ...) until one is missing. */
    private static List<Identifier> discover(String id, String anim) {
        var resources = Minecraft.getInstance().getResourceManager();
        List<Identifier> out = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            Identifier frame = Identifier.fromNamespaceAndPath(MOD_ID,
                    (TEXTURE_DIR + "/%s/%s/%s_%s_%02d.png").formatted(id, anim, id, anim, i));
            if (resources.getResource(frame).isEmpty()) break;
            out.add(frame);
        }
        if (out.isEmpty()) {
            Identifier single = Identifier.fromNamespaceAndPath(MOD_ID,
                    (TEXTURE_DIR + "/%s/%s/%s_%s.png").formatted(id, anim, id, anim));
            if (resources.getResource(single).isPresent()) out.add(single);
        }
        return out;
    }

    /** idle -> hit sequence -> idle -> walking loop. elapsed < 0 means "not hovered". */
    private static Identifier currentTexture(Card card, long elapsed) {
        Identifier idle = card.idle();
        if (elapsed < 0 || idle == null) return idle;

        Frames f = frames(card);
        int hitCount = f.hitting().size();
        int introFrames = hitCount == 0 ? 0 : hitCount + 2;
        long introMs = introFrames * HIT_FRAME_MS;

        if (elapsed < introMs) {
            int step = (int) (elapsed / HIT_FRAME_MS);
            if (step == 0 || step == introFrames - 1) return idle;
            return f.hitting().get(step - 1);
        }
        if (f.walking().isEmpty()) return idle;
        int i = (int) (((elapsed - introMs) / WALK_FRAME_MS) % f.walking().size());
        return f.walking().get(i);
    }

    private static int[] spriteSize(Identifier texture) {
        if (texture == null) return new int[] {0, 0};
        int[] custom = CustomMinis.sizeOf(texture);
        if (custom != null) return custom;
        return SPRITE_SIZES.computeIfAbsent(texture, id -> {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return new int[] {0, 0};
            try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                return new int[] {image.getWidth(), image.getHeight()};
            } catch (IOException exception) {
                return new int[] {0, 0};
            }
        });
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(returnScreen);
    }
}