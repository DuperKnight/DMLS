package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ActivityWaveModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking an activity wave check. */
public final class ActivityWaveScreen extends DMLSMenuScreen {
    private final ActivityWaveModule module;
    private TextFieldWidget ignsField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public ActivityWaveScreen(Screen parent, ActivityWaveModule module) {
        super(Text.translatable("dmls.module.activity.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(64));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignsField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.player_igns")), scaled(14));
        ignsField.setMaxLength(1024);
        ignsField.setSuggestion(Text.translatable("dmls.placeholder.player_names_many").getString());
        ignsField.setChangedListener(value -> {
            ignsField.setSuggestion(value.isEmpty()
                    ? Text.translatable("dmls.placeholder.player_names_many").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(ignsField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.check_activity"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_igns");
            return;
        }
        ActivityWaveModule.SubmitStatus status = module.submit(client, input);
        if (status == ActivityWaveModule.SubmitStatus.STARTED) {
            closeToGame();
            return;
        }
        validationMessage = switch (status) {
            case INVALID -> Text.translatable("dmls.validation.player_igns");
            case TOO_MANY -> Text.translatable("dmls.chat.activity.too_many", ActivityWaveModule.MAX_PLAYERS);
            case BUSY -> Text.translatable("dmls.validation.operation.busy");
            case BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
            case FAILED -> Text.translatable("dmls.validation.operation.start_failed");
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
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.player_igns.label"), ignsField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
