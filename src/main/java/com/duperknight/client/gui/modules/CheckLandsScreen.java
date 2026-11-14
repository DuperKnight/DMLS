package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.session.OperationStartResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Form for invoking Check Lands' existing single or batch checker. */
public final class CheckLandsScreen extends DMLSMenuScreen {
    private final CheckLandsModule module;
    private EditBox ignField;
    private Button submitButton;
    private Component validationMessage = Component.empty();

    public CheckLandsScreen(Screen parent, CheckLandsModule module) {
        super(Component.translatable("dmls.module.check_lands.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(64));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.field.player_igns")), scaled(14));
        ignField.setMaxLength(512);
        ignField.setSuggestion(Component.translatable("dmls.placeholder.player_names").getString());
        ignField.setResponder(value -> {
            ignField.setSuggestion(value.isEmpty() ? Component.translatable("dmls.placeholder.player_names").getString() : null);
            validationMessage = Component.empty();
        });
        setInitialFocus(ignField);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.submit"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        String input = ignField.getValue().trim();
        if (input.isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.player_igns");
            return;
        }
        OperationStartResult result = module.submit(minecraft, input);
        if (result == OperationStartResult.STARTED) {
            closeToGame();
            return;
        }
        validationMessage = operationError(result, "dmls.validation.player_igns");
    }

    private static Component operationError(OperationStartResult result, String invalidKey) {
        return switch (result) {
            case INVALID -> Component.translatable(invalidKey);
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.operation.blocked");
            case BUSY -> Component.translatable("dmls.validation.operation.busy");
            case FAILED_TO_START -> Component.translatable("dmls.validation.operation.start_failed");
            case STARTED -> Component.empty();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.field.player_igns.label"), ignField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
