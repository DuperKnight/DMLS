package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventPlayNearEffectModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Applies a status effect to every player within a given radius. */
public final class EventPlayNearEffectScreen extends DMLSMenuScreen {
    private final EventPlayNearEffectModule module;
    private TextFieldWidget effectField;
    private TextFieldWidget durationField;
    private TextFieldWidget amplifierField;
    private TextFieldWidget radiusField;
    private Text status = Text.empty();

    public EventPlayNearEffectScreen(Screen parent, EventPlayNearEffectModule module) {
        super(Text.translatable("dmls.module.event_play_near_effect.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(146));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        effectField = new TextFieldWidget(textRenderer, x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_play_near_effect.effect_field"));
        effectField.setMaxLength(EventPlayNearEffectModule.MAX_EFFECT_NAME_LENGTH);
        effectField.setPlaceholder(Text.translatable("dmls.module.event_play_near_effect.effect_placeholder"));
        addScrollableChild(effectField, 0);

        durationField = new TextFieldWidget(textRenderer, x, contentY(scaled(24)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_play_near_effect.duration_field"));
        durationField.setMaxLength(7);
        durationField.setPlaceholder(Text.translatable("dmls.module.event_play_near_effect.duration_placeholder"));
        addScrollableChild(durationField, scaled(24));

        amplifierField = new TextFieldWidget(textRenderer, x, contentY(scaled(48)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_play_near_effect.amplifier_field"));
        amplifierField.setMaxLength(3);
        amplifierField.setPlaceholder(Text.translatable("dmls.module.event_play_near_effect.amplifier_placeholder"));
        addScrollableChild(amplifierField, scaled(48));

        radiusField = new TextFieldWidget(textRenderer, x, contentY(scaled(72)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_play_near_effect.radius_field"));
        radiusField.setMaxLength(4);
        radiusField.setPlaceholder(Text.translatable("dmls.module.event_play_near_effect.radius_placeholder"));
        addScrollableChild(radiusField, scaled(72));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_play_near_effect.run"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            EventPlayNearEffectModule.RunResult result = module.run(client,
                    effectField.getText(), durationField.getText(),
                    amplifierField.getText(), radiusField.getText());
            status = statusFor(result);
        }).dimensions(x, contentY(scaled(96)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(96));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private Text statusFor(EventPlayNearEffectModule.RunResult result) {
        return switch (result) {
            case SENT -> Text.translatable("dmls.chat.event_play_near_effect.sent");
            case SIMULATED -> Text.translatable("dmls.chat.dry_run.status.on");
            case INVALID_EFFECT -> Text.translatable("dmls.validation.event_play_near_effect.effect");
            case INVALID_DURATION -> Text.translatable("dmls.validation.event_play_near_effect.duration");
            case INVALID_AMPLIFIER -> Text.translatable("dmls.validation.event_play_near_effect.amplifier");
            case INVALID_RADIUS -> Text.translatable("dmls.validation.event_play_near_effect.radius");
            case RANK_BLOCKED -> Text.translatable("dmls.chat.department.required",
                    com.duperknight.client.modules.StaffDepartment.EVENTS.displayName());
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.server_blocked");
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(124));
        if (!status.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, statusY, 0xFFDDDDDD);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}