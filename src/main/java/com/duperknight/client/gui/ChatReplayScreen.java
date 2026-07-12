package com.duperknight.client.gui;

import com.duperknight.client.modules.ChatReplayModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scrollable log of the session chat with a player or text filter. */
public final class ChatReplayScreen extends DMLSMenuScreen {
    private static final int REBUILD_INTERVAL_TICKS = 40;

    private final ChatReplayModule module;
    private TextFieldWidget filterField;
    private String filter;
    private List<OrderedText> lines = List.of();
    private int lastEntryCount = -1;
    private int lineHeight;
    private int contentWidth;
    private int ticksSinceRebuild;

    public ChatReplayScreen(Screen parent, ChatReplayModule module, String initialFilter) {
        super(Text.translatable("dmls.module.chat_replay.name"), parent);
        this.module = module;
        this.filter = initialFilter;
    }

    @Override
    protected void init() {
        filterField = addDrawableChild(new TextFieldWidget(textRenderer, leftPairedButtonX(), footerButtonY(),
                pairedButtonWidth(), STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.filter")));
        filterField.setMaxLength(64);
        filterField.setText(filter);
        filterField.setSuggestion(filter.isEmpty() ? Text.translatable("dmls.placeholder.filter").getString() : null);
        filterField.setChangedListener(value -> {
            filter = value;
            filterField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.filter").getString() : null);
            rebuild();
        });

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        rebuild();
    }

    private void rebuild() {
        lastEntryCount = ChatReplayModule.entryCount();
        ticksSinceRebuild = 0;
        contentWidth = Math.min(scaled(380), width - scaled(40));
        lineHeight = Math.max(textRenderer.fontHeight + 1, scaled(10));

        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<OrderedText> built = new ArrayList<>();
        for (ChatReplayModule.Entry entry : ChatReplayModule.entries()) {
            if (!needle.isEmpty() && !entry.cleanText().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            Text line = Text.literal("§8[" + entry.time() + "] §r").append(entry.text());
            built.addAll(textRenderer.wrapLines(line, contentWidth - scaled(8)));
        }
        lines = built;
        configureScrollableContent(module, Math.max(scaled(40), lines.size() * lineHeight + scaled(8)));
    }

    @Override
    public void tick() {
        ticksSinceRebuild++;
        if (ticksSinceRebuild >= REBUILD_INTERVAL_TICKS && ChatReplayModule.entryCount() != lastEntryCount) {
            rebuild();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);

        int x = (width - contentWidth) / 2 + scaled(4);
        if (lines.isEmpty()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.screen.chat_replay.empty"),
                        width / 2, emptyY, 0xFFAAAAAA);
            }
        } else {
            for (int index = 0; index < lines.size(); index++) {
                int y = contentY(index * lineHeight);
                if (isContentVisible(y, textRenderer.fontHeight)) {
                    context.drawTextWithShadow(textRenderer, lines.get(index), x, y, 0xFFFFFFFF);
                }
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
