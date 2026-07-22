package com.duperknight.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shared, bounded confirmation screen for commands with destructive or bulk effects. */
public final class DangerReviewScreen extends DMLSMenuScreen {
    private final List<Text> preview;
    private final BooleanSupplier confirmationActive;
    private final BooleanSupplier confirmer;
    private final Runnable invalidator;
    private final Text confirmLabel;

    private final List<OrderedText> wrappedPreview = new ArrayList<>();
    private ButtonWidget confirmButton;
    private Text status = Text.empty();
    private boolean completed;

    public DangerReviewScreen(Screen parent, Text title, List<Text> preview, Text confirmLabel,
                              BooleanSupplier confirmationActive, BooleanSupplier confirmer,
                              Runnable invalidator) {
        super(title, parent);
        this.preview = List.copyOf(preview);
        this.confirmLabel = Objects.requireNonNull(confirmLabel);
        this.confirmationActive = Objects.requireNonNull(confirmationActive);
        this.confirmer = Objects.requireNonNull(confirmer);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    @Override
    protected void init() {
        wrappedPreview.clear();
        int previewWidth = Math.min(scaled(420), width - scaled(48));
        for (Text line : preview) {
            wrappedPreview.addAll(textRenderer.wrapLines(line, previewWidth - scaled(16)));
        }
        configureScrollableContent(headerHeight() + scaled(34),
                Math.max(scaled(70), wrappedPreview.size() * scaled(12) + scaled(28)));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        confirmButton = addDrawableChild(ButtonWidget.builder(confirmLabel.copy().styled(style -> style.withColor(0xFFFF5555)),
                        button -> confirm())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        confirmButton.active = confirmationActive.getAsBoolean();
    }

    private void confirm() {
        if (!confirmationActive.getAsBoolean()) {
            expire();
            return;
        }
        confirmButton.active = false;
        if (confirmer.getAsBoolean()) {
            completed = true;
            closeToGame();
        } else {
            status = Text.translatable("dmls.screen.confirmation.failed");
            confirmButton.active = confirmationActive.getAsBoolean();
        }
    }

    private void expire() {
        confirmButton.active = false;
        status = Text.translatable("dmls.screen.confirmation.expired");
    }

    @Override
    public void tick() {
        if (confirmButton != null && !confirmationActive.getAsBoolean()) expire();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, headerHeight() + scaled(16), 0xFFFFAA00);

        beginContentScissor(context);
        int panelWidth = Math.min(scaled(420), width - scaled(48));
        int panelHeight = Math.max(scaled(50), wrappedPreview.size() * scaled(12) + scaled(16));
        renderPanel(context, (width - panelWidth) / 2, contentY(0), panelWidth, panelHeight);
        int first = firstVisibleContentIndex(scaled(12));
        int end = visibleContentEndIndex(scaled(12), wrappedPreview.size());
        for (int index = first; index < end; index++) {
            int y = contentY(scaled(8) + index * scaled(12));
            if (isContentVisible(y, textRenderer.fontHeight)) {
                context.drawTextWithShadow(textRenderer, wrappedPreview.get(index),
                        (width - panelWidth) / 2 + scaled(8), y, 0xFFE0E0E0);
            }
        }
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (!completed) invalidator.run();
        super.close();
    }
}
