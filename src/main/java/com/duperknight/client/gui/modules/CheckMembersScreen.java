package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.CheckMembersModule;
import com.duperknight.client.session.OperationStartResult;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking Check Members' land member listing. */
public final class CheckMembersScreen extends DMLSMenuScreen {
    private final CheckMembersModule module;
    private TextFieldWidget landField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public CheckMembersScreen(Screen parent, CheckMembersModule module) {
        super(Text.translatable("dmls.module.check_members.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(64));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        landField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.land_name")), scaled(14));
        landField.setMaxLength(64);
        landField.setSuggestion(Text.translatable("dmls.placeholder.land_name").getString());
        landField.setChangedListener(value -> {
            landField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.land_name").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(landField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.button.submit"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        String input = landField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.land_name");
            return;
        }
        OperationStartResult result = module.submit(client, input);
        if (result == OperationStartResult.STARTED) {
            closeToGame();
            return;
        }
        validationMessage = switch (result) {
            case INVALID -> Text.translatable("dmls.validation.land_name");
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
            case BUSY -> Text.translatable("dmls.validation.operation.busy");
            case FAILED_TO_START -> Text.translatable("dmls.validation.operation.start_failed");
            case STARTED -> Text.empty();
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.land_name.label"), landField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
