package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.AwayModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Controls for the away responder. */
public final class AwayScreen extends DMLSMenuScreen {
    private final AwayModule module;
    private EditBox durationField;
    private Button brbButton;
    private Button dndButton;
    private Button backNowButton;
    private Component validationMessage = Component.empty();

    public AwayScreen(Screen parent, AwayModule module) {
        super(Component.translatable("dmls.module.away.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(112));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        durationField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.brb_duration")), scaled(14));
        durationField.setMaxLength(16);
        durationField.setSuggestion("5m");
        durationField.setResponder(value -> {
            durationField.setSuggestion(value.isEmpty() ? "5m" : null);
            validationMessage = Component.empty();
        });

        brbButton = addScrollableChild(Button.builder(brbButtonText(), button -> startBrb())
                .bounds(formX, contentY(scaled(42)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        dndButton = addScrollableChild(Button.builder(dndButtonText(), button -> startDnd())
                .bounds(formX + formWidth / 2 + scaled(2), contentY(scaled(42)), formWidth / 2 - scaled(2), STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        backNowButton = addScrollableChild(Button.builder(Component.translatable("dmls.button.back_now"), button -> stopAway())
                .bounds(formX, contentY(scaled(70)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(70));
        refreshActions();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void startBrb() {
        String input = durationField.getValue().trim();
        if (AwayModule.parseDurationMillis(input).isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.brb_duration");
            return;
        }
        validationMessage = Component.empty();
        if (module.startBrb(minecraft, input)) {
            closeToGame();
        } else {
            validationMessage = Component.translatable("dmls.validation.brb_duration");
        }
    }

    private void startDnd() {
        module.setDnd(minecraft, true);
        closeToGame();
    }

    private void stopAway() {
        module.disable(minecraft);
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

    private Component brbButtonText() {
        return Component.translatable(module.mode() == AwayModule.Mode.BRB
                ? "dmls.button.restart_brb"
                : "dmls.button.start_brb");
    }

    private Component dndButtonText() {
        return Component.translatable(module.mode() == AwayModule.Mode.DND
                ? "dmls.button.dnd_active"
                : "dmls.button.start_dnd");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.field.brb_duration.label"), durationField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(98));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
