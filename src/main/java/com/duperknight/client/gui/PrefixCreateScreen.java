package com.duperknight.client.gui;

import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.PrefixTextFormatter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Form for creating a formatted server prefix. */
public final class PrefixCreateScreen extends DMLSMenuScreen {
    private static final int PREVIEW_HEIGHT = 24;

    private final PrefixCreateModule module;
    private TextFieldWidget ignField;
    private TextFieldWidget prefixIdField;
    private TextFieldWidget prefixTextField;
    private TextFieldWidget customLimitField;
    private String limit = PrefixCreateModule.LIMITS.get(0);
    private ButtonWidget submitButton;
    private PrefixTextFormatter.ParseResult preview = PrefixTextFormatter.parse("");
    private PrefixCreateModule.ValidationResult validation = PrefixCreateModule.ValidationResult.error("");

    public PrefixCreateScreen(Screen parent, PrefixCreateModule module) {
        super(Text.literal("Prefix Creation"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedIgn = fieldText(ignField);
        String savedPrefixId = fieldText(prefixIdField);
        String savedPrefixText = fieldText(prefixTextField);
        String savedCustomLimit = fieldText(customLimitField);

        configureScrollableContent(module, scaled(370));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int splitWidth = (formWidth * 2) / 3;
        int customWidth = formWidth - splitWidth - scaled(4);

        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.literal("Player IGN")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setText(savedIgn);
        ignField.setSuggestion(savedIgn.isEmpty() ? "PlayerName" : null);
        ignField.setChangedListener(value -> {
            ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null);
            refreshValidation();
        });
        setInitialFocus(ignField);

        prefixIdField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.literal("Prefix ID")), scaled(60));
        prefixIdField.setMaxLength(64);
        prefixIdField.setText(savedPrefixId);
        prefixIdField.setSuggestion(savedPrefixId.isEmpty() ? "prefixid" : null);
        prefixIdField.setChangedListener(value -> {
            prefixIdField.setSuggestion(value.isEmpty() ? "prefixid" : null);
            refreshValidation();
        });

        prefixTextField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(106)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.literal("Prefix text")), scaled(106));
        prefixTextField.setMaxLength(PrefixCreateModule.MAX_COMMAND_LENGTH);
        prefixTextField.setText(savedPrefixText);
        prefixTextField.setSuggestion(savedPrefixText.isEmpty() ? "&aHelper <gold>Team" : null);
        prefixTextField.setChangedListener(value -> {
            prefixTextField.setSuggestion(value.isEmpty() ? "&aHelper <gold>Team" : null);
            refreshValidation();
        });

        addScrollableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), limit)
                .values(PrefixCreateModule.LIMITS)
                .build(formX, contentY(scaled(204)), splitWidth, STANDARD_BUTTON_HEIGHT,
                        Text.literal("Player limit"), (button, value) -> {
                            limit = value;
                            updateCustomLimitState();
                            refreshValidation();
                        }), scaled(204));

        customLimitField = addScrollableChild(new TextFieldWidget(textRenderer, formX + splitWidth + scaled(4),
                contentY(scaled(204)), customWidth, STANDARD_BUTTON_HEIGHT, Text.literal("Custom")), scaled(204));
        customLimitField.setMaxLength(10);
        customLimitField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(character -> character >= '0' && character <= '9'));
        customLimitField.setText(savedCustomLimit);
        customLimitField.setSuggestion(savedCustomLimit.isEmpty() ? "Custom" : null);
        customLimitField.setChangedListener(value -> {
            customLimitField.setSuggestion(value.isEmpty() ? "Custom" : null);
            refreshValidation();
        });
        updateCustomLimitState();

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        refreshValidation();
    }

    private static String fieldText(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    private void updateCustomLimitState() {
        if (customLimitField == null) {
            return;
        }
        boolean custom = PrefixCreateModule.CUSTOM_LIMIT.equals(limit);
        customLimitField.active = custom;
        customLimitField.setEditable(custom);
        if (!custom) {
            customLimitField.setFocused(false);
            customLimitField.setCursorToEnd(false);
        }
    }

    private String selectedLimit() {
        return PrefixCreateModule.CUSTOM_LIMIT.equals(limit) ? customLimitField.getText().trim() : limit;
    }

    private void refreshValidation() {
        if (ignField == null || prefixIdField == null || prefixTextField == null || customLimitField == null) {
            return;
        }
        preview = PrefixTextFormatter.parse(prefixTextField.getText());
        validation = PrefixCreateModule.validate(ignField.getText().trim(), selectedLimit(), prefixIdField.getText().trim(), prefixTextField.getText());
        if (submitButton != null) {
            submitButton.active = !ClientUtils.isNotConnected(client) && validation.valid();
        }
    }

    private void submit() {
        PrefixCreateModule.ValidationResult result = module.submit(client, ignField.getText().trim(), selectedLimit(),
                prefixIdField.getText().trim(), prefixTextField.getText());
        validation = result;
        if (result.valid()) {
            closeToGame();
        }
    }

    @Override
    public void tick() {
        refreshValidation();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        drawContentLabel(context, Text.literal("Player IGN:"), ignField.getX(), contentY(0));
        drawContentLabel(context, Text.literal("Prefix ID:"), prefixIdField.getX(), contentY(scaled(46)));
        drawContentLabel(context, Text.literal("Prefix text:"), prefixTextField.getX(), contentY(scaled(92)));
        renderPreview(context, formX, formWidth);
        drawContentLabel(context, Text.literal("Player limit:"), formX, contentY(scaled(190)));
        renderCommandStatus(context, formX, formWidth);
        renderValidation(context, formWidth);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPreview(DrawContext context, int formX, int formWidth) {
        int labelY = contentY(scaled(138));
        int previewY = contentY(scaled(152));
        drawContentLabel(context, Text.literal("Preview:"), formX, labelY);
        if (!isContentVisible(previewY, PREVIEW_HEIGHT)) {
            return;
        }
        renderPanel(context, formX, previewY, formWidth, PREVIEW_HEIGHT);
        if (preview.valid()) {
            context.enableScissor(formX + scaled(3), previewY + scaled(3), formX + formWidth - scaled(3), previewY + PREVIEW_HEIGHT - scaled(3));
            int previewTextY = previewY + (PREVIEW_HEIGHT - textRenderer.fontHeight) / 2 + 1;
            context.drawTextWithShadow(textRenderer, preview.preview(), formX + scaled(6), previewTextY, 0xFFFFFFFF);
            context.disableScissor();
        }
    }

    private void renderCommandStatus(DrawContext context, int formX, int formWidth) {
        int commandLength = PrefixCreateModule.createCommand(prefixIdField.getText().trim(), prefixTextField.getText()).length();
        int statusY = contentY(scaled(238));
        int color = commandLength > PrefixCreateModule.MAX_COMMAND_LENGTH ? 0xFFFF5555 : 0xFFAAAAAA;
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.literal("Create command: " + commandLength + "/" + PrefixCreateModule.MAX_COMMAND_LENGTH),
                    formX, statusY, color);
        }
    }

    private void renderValidation(DrawContext context, int formWidth) {
        if (validation.valid() || validation.message().isEmpty()) {
            return;
        }
        int y = contentY(scaled(258));
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(validation.message()), formWidth);
        for (OrderedText line : lines) {
            if (isContentVisible(y, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFFF5555);
            }
            y += scaled(12);
        }
    }

    private void drawContentLabel(DrawContext context, Text label, int x, int y) {
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, label, x, y, 0xFFCCCCCC);
        }
    }
}
