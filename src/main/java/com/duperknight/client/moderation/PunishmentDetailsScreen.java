package com.duperknight.client.moderation;

import com.duperknight.client.gui.DMLSMenuScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Read-only details for one punishment-log row. */
public final class PunishmentDetailsScreen extends DMLSMenuScreen {
    private final PunishmentLogEntry entry;

    public PunishmentDetailsScreen(Screen parent, PunishmentLogEntry entry) {
        super(Component.translatable("dmls.moderation.details.title"), parent);
        this.entry = entry;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(scaled(240), width - scaled(32));
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds((width - buttonWidth) / 2, footerButtonY(), buttonWidth, STANDARD_BUTTON_HEIGHT)
                .build());
        int panelWidth = detailPanelWidth();
        int panelY = detailPanelY();
        configureScrollableContent(panelY + 1, detailContentHeight(panelWidth) + 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        int panelWidth = detailPanelWidth();
        int panelX = (width - panelWidth) / 2;
        int panelY = detailPanelY();
        int panelHeight = Math.max(1, detailPanelBottom() - panelY);
        renderPanel(context, panelX, panelY, panelWidth, panelHeight);

        int x = panelX + scaled(12);
        int valueX = x + scaled(92);
        int y = contentY(scaled(11));
        beginContentScissor(context);
        context.centeredText(font, title, width / 2, y, 0xFFFFFFFF);
        y += scaled(24);
        drawRow(context, "dmls.moderation.details.player", entry.playerName(), x, valueX, y);
        y += scaled(22);
        drawRow(context, "dmls.moderation.details.type", entry.type().displayName().getString(), x, valueX, y);
        y += scaled(22);
        drawRow(context, "dmls.moderation.details.staff", entry.staffName(), x, valueX, y);
        y += scaled(22);
        if (!entry.duration().isEmpty()) {
            drawRow(context, "dmls.moderation.details.duration", entry.duration(), x, valueX, y);
            y += scaled(22);
        }
        if (!entry.occurredAt().isEmpty()) {
            drawRow(context, "dmls.moderation.details.date", entry.occurredAt(), x, valueX, y);
            y += scaled(22);
        }
        if (!entry.expiresAt().isEmpty()) {
            drawRow(context, "dmls.moderation.details.expires", entry.expiresAt(), x, valueX, y);
            y += scaled(22);
        }

        y += scaled(3);
        context.text(font, Component.translatable("dmls.moderation.details.reason"),
                x, y, 0xFFAAAAAA);
        y += scaled(13);
        String reason = entry.reason().isEmpty()
                ? Component.translatable("dmls.moderation.details.unknown").getString() : entry.reason();
        List<FormattedCharSequence> reasonLines = font.split(Component.literal(reason), panelWidth - scaled(24));
        for (FormattedCharSequence line : reasonLines) {
            context.text(font, line, x, y, 0xFFFFFFFF);
            y += font.lineHeight + scaled(3);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    protected int contentScrollbarX() {
        return width / 2 + detailPanelWidth() / 2 + scaled(7);
    }

    private int detailPanelWidth() {
        return Math.min(scaled(420), width - scaled(32));
    }

    private int detailPanelY() {
        return headerHeight() + scaled(10);
    }

    private int detailPanelBottom() {
        return Math.max(detailPanelY() + 1, height - FOOTER_TOP_OFFSET - scaled(8));
    }

    private int detailContentHeight(int panelWidth) {
        int height = scaled(12) + scaled(24) + scaled(22) * 3;
        if (!entry.duration().isEmpty()) height += scaled(22);
        if (!entry.occurredAt().isEmpty()) height += scaled(22);
        if (!entry.expiresAt().isEmpty()) height += scaled(22);
        String reason = entry.reason().isEmpty()
                ? Component.translatable("dmls.moderation.details.unknown").getString() : entry.reason();
        int reasonLines = Math.max(1, font.split(Component.literal(reason), panelWidth - scaled(24)).size());
        return height + scaled(3) + scaled(13)
                + reasonLines * (font.lineHeight + scaled(3)) + scaled(12);
    }

    private void drawRow(GuiGraphicsExtractor context, String key, String value, int x, int valueX, int y) {
        context.text(font, Component.translatable(key), x, y, 0xFFAAAAAA);
        context.text(font, Component.literal(value), valueX, y, 0xFFFFFFFF);
    }
}
