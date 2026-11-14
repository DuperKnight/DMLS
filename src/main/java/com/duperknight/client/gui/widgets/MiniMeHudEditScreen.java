package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.MiniMeHudFilePicker;
import com.duperknight.client.modules.CustomMinis;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Edits one custom mini. Every image you choose is copied into the mini's folder immediately,
 * so there is no Save button; the name is saved when you leave the screen.
 */
public final class MiniMeHudEditScreen extends DMLSMenuScreen {
    private static final int SLOT = 30;
    private static final int SLOT_GAP = 4;
    private static final int LABEL_HEIGHT = 11;
    private static final int SECTION_GAP = 8;
    private static final int SECTIONS_TOP = 56;
    private static final int REMOVE_SIZE = 10;
    private static final int COLUMN_GAP = 12;
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_SPRITE = 52;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_COLOR = 0x99000000;
    private static final int DONE_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int NAME_WIDTH = 160;
    private static final int MAX_NAME_LENGTH = 24;

    private static final int COLOR_SLOT = 0xAA101418;
    private static final int COLOR_SLOT_HOVER = 0xCC1E262C;
    private static final int COLOR_BORDER = 0xFF555B61;
    private static final int COLOR_BORDER_HOVER = 0xFFAAB2B8;

    private static final long PREVIEW_IDLE_MS = 700;
    private static final long PREVIEW_WALK_MS = 2400;

    private static final CustomMinis.Group[] GROUPS = CustomMinis.Group.values();

    private final Screen returnScreen;
    private final String miniId;
    private EditBox nameBox;
    private Component status = Component.empty();
    private final long openedAt = System.currentTimeMillis();

    public MiniMeHudEditScreen(Screen parent, String miniId) {
        super(Component.translatable("dmls.module.mini_me_hud.edit.title"), parent);
        this.returnScreen = parent;
        this.miniId = miniId;
    }

    @Override
    protected void init() {
        CustomMinis.CustomMini mini = CustomMinis.find(miniId);

        nameBox = new EditBox(font, width / 2 - NAME_WIDTH / 2, 26, NAME_WIDTH, BUTTON_HEIGHT,
                Component.translatable("dmls.module.mini_me_hud.edit.name"));
        nameBox.setMaxLength(MAX_NAME_LENGTH);
        nameBox.setValue(mini == null ? "" : mini.name());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - DONE_WIDTH / 2, height - 28, DONE_WIDTH, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- layout

    private int perRow() {
        int available = width - 24 - PREVIEW_WIDTH - COLUMN_GAP;
        return Math.clamp(available / (SLOT + SLOT_GAP), 2, 8);
    }

    private int slotsWidth() {
        return perRow() * (SLOT + SLOT_GAP) - SLOT_GAP;
    }

    private int contentWidth() {
        return slotsWidth() + COLUMN_GAP + PREVIEW_WIDTH;
    }

    private int leftX() {
        return (width - contentWidth()) / 2;
    }

    private int previewX() {
        return leftX() + slotsWidth() + COLUMN_GAP;
    }

    /** Slots shown for a group: the images it has, plus one empty "+" slot while there is room. */
    private static int itemsIn(CustomMinis.Group group, CustomMinis.CustomMini mini) {
        if (group == CustomMinis.Group.IDLE) return 1;
        int count = framesOf(mini, group).size();
        return count + (count < CustomMinis.MAX_FRAMES ? 1 : 0);
    }

    private static List<CustomMinis.Sprite> framesOf(CustomMinis.CustomMini mini, CustomMinis.Group group) {
        return switch (group) {
            case IDLE -> mini.idle() == null ? List.of() : List.of(mini.idle());
            case WALKING -> mini.walking();
            case HITTING -> mini.hitting();
        };
    }

    private int sectionHeight(int items) {
        int rows = Math.max(1, (items + perRow() - 1) / perRow());
        return LABEL_HEIGHT + rows * (SLOT + SLOT_GAP) - SLOT_GAP;
    }

    private int groupTop(CustomMinis.Group group, CustomMinis.CustomMini mini) {
        int y = SECTIONS_TOP;
        if (group == CustomMinis.Group.IDLE) return y;
        y += sectionHeight(1) + SECTION_GAP;
        if (group == CustomMinis.Group.WALKING) return y;
        return y + sectionHeight(itemsIn(CustomMinis.Group.WALKING, mini)) + SECTION_GAP;
    }

    private int slotX(int index) {
        return leftX() + (index % perRow()) * (SLOT + SLOT_GAP);
    }

    private int slotY(CustomMinis.Group group, CustomMinis.CustomMini mini, int index) {
        return groupTop(group, mini) + LABEL_HEIGHT + (index / perRow()) * (SLOT + SLOT_GAP);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- clicks

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            CustomMinis.CustomMini mini = CustomMinis.find(miniId);
            if (mini != null) {
                for (CustomMinis.Group group : GROUPS) {
                    int items = itemsIn(group, mini);
                    int filledCount = framesOf(mini, group).size();
                    for (int i = 0; i < items; i++) {
                        int sx = slotX(i);
                        int sy = slotY(group, mini, i);
                        if (!inside(click.x(), click.y(), sx, sy, SLOT, SLOT)) continue;

                        boolean filled = i < filledCount;
                        if (filled && inside(click.x(), click.y(), sx + SLOT - REMOVE_SIZE, sy,
                                REMOVE_SIZE, REMOVE_SIZE)) {
                            remove(group, i);
                        } else {
                            choose(group, i, filled);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void choose(CustomMinis.Group group, int index, boolean filled) {
        boolean adding = group != CustomMinis.Group.IDLE && !filled;
        String title = Component.translatable("dmls.module.mini_me_hud.edit." + (group == CustomMinis.Group.IDLE
                ? "pick_idle" : adding ? "pick_frames" : "pick_frame")).getString();

        // The dialog opens on its own thread; this runs back on the game thread once a choice is made.
        MiniMeHudFilePicker.pickPngs(title, adding, picked -> {
            if (picked.isEmpty()) return;

            boolean ok;
            if (group == CustomMinis.Group.IDLE) {
                ok = CustomMinis.setIdle(miniId, picked.get(0));
            } else if (adding) {
                ok = CustomMinis.addFrames(miniId, group, picked) == picked.size();
            } else {
                ok = CustomMinis.replaceFrame(miniId, group, index, picked.get(0));
            }
            finish(ok);
        });
    }

    private void remove(CustomMinis.Group group, int index) {
        boolean ok = group == CustomMinis.Group.IDLE
                ? CustomMinis.clearIdle(miniId)
                : CustomMinis.removeFrame(miniId, group, index);
        finish(ok);
    }

    private void finish(boolean ok) {
        status = ok ? Component.empty() : Component.translatable("dmls.module.mini_me_hud.edit.import_failed");
        if (ok) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        CustomMinis.CustomMini mini = CustomMinis.find(miniId);
        if (mini == null) {
            // The mini was deleted while this screen was open.
            minecraft.schedule(this::onClose);
            return;
        }

        renderMenuBackground(context);
        context.fill(leftX() - PANEL_PADDING, 8, leftX() + contentWidth() + PANEL_PADDING,
                height - 8, PANEL_COLOR);
        context.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);

        for (CustomMinis.Group group : GROUPS) {
            drawGroup(context, mini, group, mouseX, mouseY);
        }
        drawPreview(context, mini);

        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2, height - 42, 0xFFFF5555);
        }
    }

    private void drawGroup(GuiGraphicsExtractor context, CustomMinis.CustomMini mini,
                           CustomMinis.Group group, int mouseX, int mouseY) {
        int top = groupTop(group, mini);
        Component label = Component.translatable(
                "dmls.module.mini_me_hud.edit." + group.name().toLowerCase(Locale.ROOT));
        context.centeredText(font, label, leftX() + font.width(label) / 2, top, 0xFFFFFFFF);

        List<CustomMinis.Sprite> frames = framesOf(mini, group);
        int items = itemsIn(group, mini);
        for (int i = 0; i < items; i++) {
            int sx = slotX(i);
            int sy = slotY(group, mini, i);
            CustomMinis.Sprite sprite = i < frames.size() ? frames.get(i) : null;
            drawSlot(context, sx, sy, sprite, group != CustomMinis.Group.IDLE ? i + 1 : 0, mouseX, mouseY);
        }
    }

    /** One image slot. {@code number} is the 1-based frame number, or 0 for the idle slot. */
    private void drawSlot(GuiGraphicsExtractor context, int x, int y, CustomMinis.Sprite sprite,
                          int number, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, SLOT, SLOT);
        context.fill(x, y, x + SLOT, y + SLOT, hovered ? COLOR_BORDER_HOVER : COLOR_BORDER);
        context.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);

        if (sprite == null) {
            // Empty slot: a "+" to add an image.
            int cx = x + SLOT / 2;
            int cy = y + SLOT / 2;
            int color = hovered ? 0xFFFFFFFF : 0xFF8A9096;
            context.fill(cx - 6, cy - 1, cx + 6, cy + 1, color);
            context.fill(cx - 1, cy - 6, cx + 1, cy + 6, color);
            return;
        }

        int inner = SLOT - 4;
        float scale = Math.min(inner / (float) sprite.width(), inner / (float) sprite.height());
        int w = Math.max(1, Math.round(sprite.width() * scale));
        int h = Math.max(1, Math.round(sprite.height() * scale));
        int left = x + (SLOT - w) / 2;
        int top = y + (SLOT - h) / 2;
        context.blit(sprite.texture(), left, top, left + w, top + h, 0.0F, 1.0F, 0.0F, 1.0F);

        if (number > 0) {
            context.centeredText(font, Component.literal(String.valueOf(number)),
                    x + 6, y + SLOT - font.lineHeight - 1, 0xFFFFFFAA);
        }
        if (hovered) {
            boolean overRemove = inside(mouseX, mouseY, x + SLOT - REMOVE_SIZE, y, REMOVE_SIZE, REMOVE_SIZE);
            context.fill(x + SLOT - REMOVE_SIZE, y, x + SLOT, y + REMOVE_SIZE,
                    overRemove ? 0xFFCC4444 : 0xFF7A2E2E);
            context.centeredText(font, Component.literal("x"), x + SLOT - REMOVE_SIZE / 2,
                    y + (REMOVE_SIZE - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
    }

    /** Live preview: idle, then the walking loop, then one swing of the hitting frames, repeating. */
    private void drawPreview(GuiGraphicsExtractor context, CustomMinis.CustomMini mini) {
        int x = previewX();
        int y = SECTIONS_TOP;
        Component label = Component.translatable("dmls.module.mini_me_hud.edit.preview");
        context.centeredText(font, label, x + font.width(label) / 2, y, 0xFFFFFFFF);

        int boxY = y + LABEL_HEIGHT;
        context.fill(x, boxY, x + PREVIEW_WIDTH, boxY + PREVIEW_WIDTH, COLOR_BORDER);
        context.fill(x + 1, boxY + 1, x + PREVIEW_WIDTH - 1, boxY + PREVIEW_WIDTH - 1, COLOR_SLOT);

        CustomMinis.Sprite base = mini.idleSprite();
        CustomMinis.Sprite current = previewSprite(mini, System.currentTimeMillis() - openedAt);
        int spriteX = x + (PREVIEW_WIDTH - PREVIEW_SPRITE) / 2;
        int spriteY = boxY + (PREVIEW_WIDTH - PREVIEW_SPRITE) / 2;

        if (base == null || current == null) {
            context.centeredText(font, Component.literal("?"), x + PREVIEW_WIDTH / 2,
                    boxY + PREVIEW_WIDTH / 2 - font.lineHeight / 2, 0xFF888888);
            return;
        }

        // Scale comes from the idle image so the mini doesn't change size between frames.
        float scale = Math.min(PREVIEW_SPRITE / (float) base.width(), PREVIEW_SPRITE / (float) base.height());
        int w = Math.max(1, Math.round(current.width() * scale));
        int h = Math.max(1, Math.round(current.height() * scale));
        int left = spriteX + (PREVIEW_SPRITE - w) / 2;
        int top = spriteY + (PREVIEW_SPRITE - h);
        context.blit(current.texture(), left, top, left + w, top + h, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    private static CustomMinis.Sprite previewSprite(CustomMinis.CustomMini mini, long elapsed) {
        CustomMinis.Sprite idle = mini.idleSprite();
        List<CustomMinis.Sprite> walking = mini.walking();
        List<CustomMinis.Sprite> hitting = mini.hitting();

        long hitMs = hitting.size() * MiniMeHudScreen.HIT_FRAME_MS;
        long cycle = PREVIEW_IDLE_MS + PREVIEW_WALK_MS + hitMs;
        long t = elapsed % cycle;

        if (t < PREVIEW_IDLE_MS) return idle;
        t -= PREVIEW_IDLE_MS;

        if (t < PREVIEW_WALK_MS) {
            if (walking.isEmpty()) return idle;
            return walking.get((int) ((t / MiniMeHudScreen.WALK_FRAME_MS) % walking.size()));
        }
        t -= PREVIEW_WALK_MS;

        return hitting.get(Math.min(hitting.size() - 1, (int) (t / MiniMeHudScreen.HIT_FRAME_MS)));
    }

    @Override
    public void onClose() {
        CustomMinis.CustomMini mini = CustomMinis.find(miniId);
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (mini != null && !name.isEmpty() && !name.equals(mini.name())) {
            CustomMinis.rename(miniId, name);
        }
        minecraft.gui.setScreen(returnScreen);
    }
}