package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventLightningModule;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Inputs for the Lightning event module (ring, line, plus or cross). */
public final class EventLightningScreen extends DMLSMenuScreen {
    private final EventLightningModule module;
    // Kept as fields so the values survive init() being re-run on window resize.
    private EventLightningModule.Shape shape = EventLightningModule.Shape.RING;
    // 12 is valid for every shape (ring radius min 5, line/plus/cross length min 10).
    private String sizeValue = "12";
    // 4 is valid for every shape (plus and cross allow at most 6 per arm).
    private String pointsValue = "4";
    private Component status = Component.empty();

    public EventLightningScreen(Screen parent, EventLightningModule module) {
        super(Component.translatable("dmls.module.event_lightning_ring.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(138));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        addScrollableChild(CycleButton.builder(
                        (EventLightningModule.Shape value) -> Component.translatable(
                                "dmls.module.event_lightning_ring.shape." + value.name().toLowerCase(Locale.ROOT)),
                        shape)
                .withValues(EventLightningModule.Shape.values())
                .create(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Component.translatable("dmls.module.event_lightning_ring.shape"),
                        (button, value) -> shape = value), 0);

        EditBox sizeField = new EditBox(font, x, contentY(scaled(30)), controlWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_lightning_ring.size"));
        sizeField.setMaxLength(3);
        sizeField.setHint(Component.translatable("dmls.module.event_lightning_ring.size"));
        sizeField.setValue(sizeValue);
        sizeField.setResponder(value -> sizeValue = value);
        addScrollableChild(sizeField, scaled(30));

        EditBox pointsField = new EditBox(font, x, contentY(scaled(60)), controlWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_lightning_ring.points"));
        pointsField.setMaxLength(3);
        pointsField.setHint(Component.translatable("dmls.module.event_lightning_ring.points"));
        pointsField.setValue(pointsValue);
        pointsField.setResponder(value -> pointsValue = value);
        addScrollableChild(pointsField, scaled(60));

        addScrollableChild(Button.builder(Component.translatable("dmls.module.event_lightning_ring.run"), button -> {
            status = resultText(module.run(minecraft, shape, sizeValue, pointsValue));
        }).bounds(x, contentY(scaled(90)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(90));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(122));
        if (isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, status, width / 2, statusY, 0xFFAAAAAA);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private Component resultText(EventLightningModule.RunResult result) {
        return switch (result) {
            case SENT -> Component.translatable("dmls.module.event_lightning_ring.status.sent")
                    .withStyle(ChatFormatting.GREEN);
            case SIMULATED -> Component.translatable("dmls.module.event_lightning_ring.status.simulated")
                    .withStyle(ChatFormatting.GRAY);
            case INVALID_RADIUS -> Component.translatable("dmls.module.event_lightning_ring.status.invalid_radius",
                            EventLightningModule.MIN_RADIUS, EventLightningModule.MAX_RADIUS)
                    .withStyle(ChatFormatting.RED);
            case INVALID_LENGTH -> Component.translatable("dmls.module.event_lightning_ring.status.invalid_length",
                            EventLightningModule.MIN_LENGTH, EventLightningModule.MAX_LENGTH)
                    .withStyle(ChatFormatting.RED);
            case INVALID_POINTS -> Component.translatable("dmls.module.event_lightning_ring.status.invalid_points",
                            EventLightningModule.MIN_POINTS, shape.maxPoints())
                    .withStyle(ChatFormatting.RED);
            case RANK_BLOCKED -> Component.translatable("dmls.module.event_lightning_ring.status.rank_blocked")
                    .withStyle(ChatFormatting.RED);
            case SERVER_BLOCKED -> Component.translatable("dmls.module.event_lightning_ring.status.server_blocked")
                    .withStyle(ChatFormatting.RED);
        };
    }
}