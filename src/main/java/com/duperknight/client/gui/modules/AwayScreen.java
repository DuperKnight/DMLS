package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.AwayModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Controls for the away responder. */
public final class AwayScreen extends DMLSMenuScreen {
    private final AwayModule module;
    private TextFieldWidget durationField;
    private ButtonWidget brbButton;
    private ButtonWidget dndButton;
    private ButtonWidget backNowButton;
    private Text validationMessage = Text.empty();

    public AwayScreen(Screen parent, AwayModule module) {
        super(Text.translatable("dmls.module.away.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(112));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        durationField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.brb_duration")), scaled(14));
        durationField.setMaxLength(16);
        durationField.setSuggestion("5m");
        durationField.setChangedListener(value -> {
            durationField.setSuggestion(value.isEmpty() ? "5m" : null);
            validationMessage = Text.empty();
        });

        brbButton = addScrollableChild(ButtonWidget.builder(brbButtonText(), button -> startBrb())
                .dimensions(formX, contentY(scaled(42)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        dndButton = addScrollableChild(ButtonWidget.builder(dndButtonText(), button -> startDnd())
                .dimensions(formX + formWidth / 2 + scaled(2), contentY(scaled(42)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        backNowButton = addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.back_now"), button -> stopAway())
                .dimensions(formX, contentY(scaled(70)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(70));
        refreshActions();

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

    private void startDnd() {
        module.setDnd(client, true);
        closeToGame();
    }

    private void stopAway() {
        module.disable(client);
        closeToGame();
    }

    @Override
    public void tick() {
        refreshActions();
    }

    private void refreshActions() {
        if (brbButton == null || dndButton == null || backNowButton == null) {
            return;
        }
        brbButton.setMessage(brbButtonText());
        dndButton.setMessage(dndButtonText());
        dndButton.active = module.mode() != AwayModule.Mode.DND;
        backNowButton.active = module.mode() != AwayModule.Mode.OFF;
    }

    private Text brbButtonText() {
        return Text.translatable(module.mode() == AwayModule.Mode.BRB
                ? "dmls.button.restart_brb"
                : "dmls.button.start_brb");
    }

    private Text dndButtonText() {
        return Text.translatable(module.mode() == AwayModule.Mode.DND
                ? "dmls.button.dnd_active"
                : "dmls.button.start_dnd");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.brb_duration.label"), durationField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(98));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
