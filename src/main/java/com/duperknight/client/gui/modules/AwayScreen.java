package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.AwayModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings screen for the away responder. */
public final class AwayScreen extends DMLSMenuScreen {
    private final AwayModule module;
    private TextFieldWidget durationField;
    private CyclingButtonWidget<Boolean> dndButton;
    private Text validationMessage = Text.empty();

    public AwayScreen(Screen parent, AwayModule module) {
        super(Text.translatable("dmls.module.away.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(150));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        dndButton = addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), module.mode() == AwayModule.Mode.DND).values(true, false)
                .build(formX, contentY(0), formWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.away.dnd_toggle"),
                        (button, value) -> module.setDnd(client, value)), 0);

        durationField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(46)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.brb_duration")), scaled(46));
        durationField.setMaxLength(16);
        durationField.setSuggestion("5m");
        durationField.setChangedListener(value -> durationField.setSuggestion(value.isEmpty() ? "5m" : null));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.start_brb"), button -> startBrb())
                .dimensions(formX, contentY(scaled(74)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(74));
        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.back_now"), button -> module.disable(client))
                .dimensions(formX + formWidth / 2 + scaled(2), contentY(scaled(74)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(74));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void startBrb() {
        String input = durationField.getText().trim();
        if (AwayModule.parseDurationMillis(input).isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.brb_duration");
            return;
        }
        validationMessage = Text.empty();
        if (module.startBrb(client, input)) {
            closeToGame();
        } else {
            validationMessage = Text.translatable("dmls.validation.brb_duration");
        }
    }

    @Override
    public void tick() {
        if (dndButton != null && dndButton.getValue() != (module.mode() == AwayModule.Mode.DND)) {
            dndButton.setValue(module.mode() == AwayModule.Mode.DND);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        int labelY = contentY(scaled(34));
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.brb_duration.label"), durationField.getX(), labelY, 0xFFCCCCCC);
        }
        int statusY = contentY(scaled(106));
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, module.statusText(), width / 2, statusY, 0xFFAAAAAA);
        }
        int validationY = contentY(scaled(122));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
