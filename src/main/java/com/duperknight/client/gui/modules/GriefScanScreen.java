package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.GriefScanModule;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking a grief scan. */
public final class GriefScanScreen extends DMLSMenuScreen {
    private final GriefScanModule module;
    private TextFieldWidget ignField;
    private TextFieldWidget timeField;
    private TextFieldWidget radiusField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public GriefScanScreen(Screen parent, GriefScanModule module) {
        super(Text.translatable("dmls.module.griefs.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(140));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setSuggestion(Text.translatable("dmls.placeholder.containers.ign").getString());
        ignField.setChangedListener(value -> {
            ignField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.containers.ign").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(ignField);

        int halfWidth = formWidth / 2 - scaled(2);
        timeField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), halfWidth,
                STANDARD_BUTTON_HEIGHT, Text.empty()), scaled(60));
        timeField.setMaxLength(16);
        timeField.setSuggestion("7d");
        timeField.setChangedListener(value -> {
            timeField.setSuggestion(value.isEmpty() ? "7d" : null);
            validationMessage = Text.empty();
        });

        radiusField = addScrollableChild(new TextFieldWidget(textRenderer, formX + halfWidth + scaled(4), contentY(scaled(60)), halfWidth,
                STANDARD_BUTTON_HEIGHT, Text.empty()), scaled(60));
        radiusField.setMaxLength(8);
        radiusField.setSuggestion("20");
        radiusField.setChangedListener(value -> {
            radiusField.setSuggestion(value.isEmpty() ? "20" : null);
            validationMessage = Text.empty();
        });

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.button.containers.scan"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        if (timeField.getText().trim().isEmpty() || radiusField.getText().trim().isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.containers");
            return;
        }
        AbstractCoreProtectScanModule.SubmissionResult result = module.submit(
                client, ignField.getText(), timeField.getText(), radiusField.getText());
        validationMessage = switch (result) {
            case INVALID_IGN -> Text.translatable("dmls.chat.common.invalid_ign");
            case INVALID_TIME -> Text.translatable("dmls.validation.co.time");
            case INVALID_RADIUS -> Text.translatable("dmls.validation.co.radius");
            case RANK_BLOCKED -> Text.translatable("dmls.validation.required_rank");
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.server_blocked");
            case BUSY -> Text.translatable("dmls.validation.scan.busy");
            case FAILED -> Text.translatable("dmls.validation.scan.start_failed");
            case STARTED, SIMULATED -> Text.empty();
        };
        if (result.accepted()) {
            closeToGame();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int ignLabelY = contentY(0);
        if (isContentVisible(ignLabelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.containers.ign.label"), ignField.getX(), ignLabelY, 0xFFCCCCCC);
        }
        int rowLabelY = contentY(scaled(48));
        if (isContentVisible(rowLabelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.co.time.label"), timeField.getX(), rowLabelY, 0xFFCCCCCC);
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.co.radius.label"), radiusField.getX(), rowLabelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(96));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
