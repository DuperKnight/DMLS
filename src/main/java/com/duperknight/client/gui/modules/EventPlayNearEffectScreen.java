package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventPlayNearEffectModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Applies a status effect to every player within a given radius. */
public final class EventPlayNearEffectScreen extends DMLSMenuScreen {
    private final EventPlayNearEffectModule module;
    private EditBox effectField;
    private EditBox durationField;
    private EditBox amplifierField;
    private EditBox radiusField;
    private Component status = Component.empty();

    public EventPlayNearEffectScreen(Screen parent, EventPlayNearEffectModule module) {
        super(Component.translatable("dmls.module.event_play_near_effect.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(146));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        effectField = new EditBox(font, x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_play_near_effect.effect_field"));
        effectField.setMaxLength(EventPlayNearEffectModule.MAX_EFFECT_NAME_LENGTH);
        effectField.setHint(Component.translatable("dmls.module.event_play_near_effect.effect_placeholder"));
        addScrollableChild(effectField, 0);

        durationField = new EditBox(font, x, contentY(scaled(24)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_play_near_effect.duration_field"));
        durationField.setMaxLength(7);
        durationField.setHint(Component.translatable("dmls.module.event_play_near_effect.duration_placeholder"));
        addScrollableChild(durationField, scaled(24));

        amplifierField = new EditBox(font, x, contentY(scaled(48)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_play_near_effect.amplifier_field"));
        amplifierField.setMaxLength(3);
        amplifierField.setHint(Component.translatable("dmls.module.event_play_near_effect.amplifier_placeholder"));
        addScrollableChild(amplifierField, scaled(48));

        radiusField = new EditBox(font, x, contentY(scaled(72)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_play_near_effect.radius_field"));
        radiusField.setMaxLength(4);
        radiusField.setHint(Component.translatable("dmls.module.event_play_near_effect.radius_placeholder"));
        addScrollableChild(radiusField, scaled(72));

        addScrollableChild(Button.builder(Component.translatable("dmls.module.event_play_near_effect.run"), button -> {
            Minecraft client = Minecraft.getInstance();
            EventPlayNearEffectModule.RunResult result = module.run(client,
                    effectField.getValue(), durationField.getValue(),
                    amplifierField.getValue(), radiusField.getValue());
            status = statusFor(result);
        }).bounds(x, contentY(scaled(96)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(96));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private Component statusFor(EventPlayNearEffectModule.RunResult result) {
        return switch (result) {
            case SENT -> Component.translatable("dmls.chat.event_play_near_effect.sent");
            case SIMULATED -> Component.translatable("dmls.chat.dry_run.status.on");
            case INVALID_EFFECT -> Component.translatable("dmls.validation.event_play_near_effect.effect");
            case INVALID_DURATION -> Component.translatable("dmls.validation.event_play_near_effect.duration");
            case INVALID_AMPLIFIER -> Component.translatable("dmls.validation.event_play_near_effect.amplifier");
            case INVALID_RADIUS -> Component.translatable("dmls.validation.event_play_near_effect.radius");
            case RANK_BLOCKED -> Component.translatable("dmls.chat.department.required",
                    com.duperknight.client.modules.StaffDepartment.EVENTS.displayName());
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.server_blocked");
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(124));
        if (!status.getString().isEmpty() && isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, status, width / 2, statusY, 0xFFDDDDDD);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}