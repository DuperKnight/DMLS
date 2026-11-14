package com.duperknight.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Shared, bounded confirmation screen for commands with destructive or bulk effects. */
public final class DangerReviewScreen extends DMLSMenuScreen {
    private final List<Component> preview;
    private final BooleanSupplier confirmationActive;
    private final BooleanSupplier confirmer;
    private final Runnable invalidator;
    private final Component confirmLabel;

    private final List<FormattedCharSequence> wrappedPreview = new ArrayList<>();
    private Button confirmButton;
    private Component status = Component.empty();
    private boolean completed;

    public DangerReviewScreen(Screen parent, Component title, List<Component> preview, Component confirmLabel,
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
        for (Component line : preview) {
            wrappedPreview.addAll(font.split(line, previewWidth - scaled(16)));
        }
        configureScrollableContent(headerHeight() + scaled(34),
                Math.max(scaled(70), wrappedPreview.size() * scaled(12) + scaled(28)));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        confirmButton = registerCommandControl(addRenderableWidget(Button.builder(
                        confirmLabel.copy().withStyle(style -> style.withColor(0xFFFF5555)), button -> confirm())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()),
                confirmationActive);
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
            status = Component.translatable("dmls.screen.confirmation.failed");
            confirmButton.active = confirmationActive.getAsBoolean();
        }
    }

    private void expire() {
        confirmButton.active = false;
        status = Component.translatable("dmls.screen.confirmation.expired");
    }

    @Override
    public void tick() {
        if (confirmButton != null && !confirmationActive.getAsBoolean()) expire();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.centeredText(font, title, width / 2, headerHeight() + scaled(16), 0xFFFFAA00);

        beginContentScissor(context);
        int panelWidth = Math.min(scaled(420), width - scaled(48));
        int panelHeight = Math.max(scaled(50), wrappedPreview.size() * scaled(12) + scaled(16));
        renderPanel(context, (width - panelWidth) / 2, contentY(0), panelWidth, panelHeight);
        int first = firstVisibleContentIndex(scaled(12));
        int end = visibleContentEndIndex(scaled(12), wrappedPreview.size());
        for (int index = first; index < end; index++) {
            int y = contentY(scaled(8) + index * scaled(12));
            if (isContentVisible(y, font.lineHeight)) {
                context.text(font, wrappedPreview.get(index),
                        (width - panelWidth) / 2 + scaled(8), y, 0xFFE0E0E0);
            }
        }
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (!completed) invalidator.run();
        super.onClose();
    }
}
