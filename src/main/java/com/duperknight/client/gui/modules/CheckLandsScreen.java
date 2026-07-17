package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking Check Lands' existing single or batch checker. */
public final class CheckLandsScreen extends DMLSMenuScreen {
    private final CheckLandsModule module;
    private TextFieldWidget ignField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public CheckLandsScreen(Screen parent, CheckLandsModule module) {
        super(Text.translatable("dmls.module.check_lands.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(64));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.player_igns")), scaled(14));
        ignField.setMaxLength(512);
        ignField.setSuggestion(Text.translatable("dmls.placeholder.player_names").getString());
        ignField.setChangedListener(value -> {
            ignField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.player_names").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(ignField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.submit"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_igns");
            return;
        }
        OperationStartResult result = module.submit(client, input);
        if (result == OperationStartResult.STARTED) {
            closeToGame();
            return;
        }
        validationMessage = operationError(result, "dmls.validation.player_igns");
    }

    private static Text operationError(OperationStartResult result, String invalidKey) {
        return switch (result) {
            case INVALID -> Text.translatable(invalidKey);
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
            case BUSY -> Text.translatable("dmls.validation.operation.busy");
            case FAILED_TO_START -> Text.translatable("dmls.validation.operation.start_failed");
            case STARTED -> Text.empty();
        };
    }

    @Override
    public void tick() {
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.player_igns.label"), ignField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
