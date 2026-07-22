package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.utils.PrefixTextFormatter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Form for creating a formatted server prefix. */
public final class PrefixCreateScreen extends DMLSMenuScreen {
    private static final int PREVIEW_HEIGHT = 24;
    private static final int COMMAND_STATUS_OFFSET = scaled(238);
    private static final int VALIDATION_OFFSET = scaled(258);
    private static final int VALIDATION_LINE_SPACING = scaled(12);
    private static final int CONTENT_BOTTOM_PADDING = scaled(10);

    private final PrefixCreateModule module;
    private TextFieldWidget ignField;
    private TextFieldWidget prefixIdField;
    private TextFieldWidget prefixTextField;
    private TextFieldWidget customLimitField;
    private String limit = PrefixCreateModule.LIMITS.get(0);
    private ButtonWidget submitButton;
    private PrefixTextFormatter.ParseResult preview = PrefixTextFormatter.parse("");
    private PrefixCreateModule.ValidationResult validation = PrefixCreateModule.ValidationResult.success("");

    public PrefixCreateScreen(Screen parent, PrefixCreateModule module) {
        super(Text.translatable("dmls.module.prefix.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedIgn = fieldText(ignField);
        String savedPrefixId = fieldText(prefixIdField);
        String savedPrefixText = fieldText(prefixTextField);
        String savedCustomLimit = fieldText(customLimitField);

        configureScrollableContent(module, contentHeight());
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int splitWidth = (formWidth * 2) / 3;
        int customWidth = formWidth - splitWidth - scaled(4);

        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setText(savedIgn);
        ignField.setSuggestion(savedIgn.isEmpty() ? translated("dmls.placeholder.player_name") : null);
        ignField.setChangedListener(value -> {
            ignField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.player_name") : null);
            refreshValidation();
        });
        setInitialFocus(ignField);

        prefixIdField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.prefix_id")), scaled(60));
        prefixIdField.setMaxLength(64);
        prefixIdField.setText(savedPrefixId);
        prefixIdField.setSuggestion(savedPrefixId.isEmpty() ? translated("dmls.placeholder.prefix_id") : null);
        prefixIdField.setChangedListener(value -> {
            prefixIdField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.prefix_id") : null);
            refreshValidation();
        });

        prefixTextField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(106)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.prefix_text")), scaled(106));
        prefixTextField.setMaxLength(PrefixCreateModule.MAX_COMMAND_LENGTH);
        prefixTextField.setText(savedPrefixText);
        prefixTextField.setSuggestion(savedPrefixText.isEmpty() ? translated("dmls.placeholder.prefix_text") : null);
        prefixTextField.setChangedListener(value -> {
            prefixTextField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.prefix_text") : null);
            refreshValidation();
        });

        addScrollableDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.field.player_limit"), PrefixCreateModule.LIMITS, limit,
                        value -> PrefixCreateModule.CUSTOM_LIMIT.equals(value)
                                ? Text.translatable("dmls.field.custom") : Text.literal(value),
                        (dropdown, value) -> {
                            limit = value;
                            updateCustomLimitState();
                            refreshValidation();
                        })
                .dimensions(formX, contentY(scaled(204)), splitWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), scaled(204));

        customLimitField = addScrollableChild(new TextFieldWidget(textRenderer, formX + splitWidth + scaled(4),
                contentY(scaled(204)), customWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.custom")), scaled(204));
        customLimitField.setMaxLength(10);
        customLimitField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(character -> character >= '0' && character <= '9'));
        customLimitField.setText(savedCustomLimit);
        customLimitField.setSuggestion(savedCustomLimit.isEmpty() ? translated("dmls.field.custom") : null);
        customLimitField.setChangedListener(value -> {
            customLimitField.setSuggestion(value.isEmpty() ? translated("dmls.field.custom") : null);
            refreshValidation();
        });
        updateCustomLimitState();

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.button.create"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()),
                () -> validation.valid());
        refreshValidation();
    }

    private static String fieldText(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    private static String translated(String key) {
        return Text.translatable(key).getString();
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
        updateScrollableContentHeight(contentHeight());
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
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        drawContentLabel(context, Text.translatable("dmls.field.player_ign.label"), ignField.getX(), contentY(0));
        drawContentLabel(context, Text.translatable("dmls.field.prefix_id.label"), prefixIdField.getX(), contentY(scaled(46)));
        drawContentLabel(context, Text.translatable("dmls.field.prefix_text.label"), prefixTextField.getX(), contentY(scaled(92)));
        renderPreview(context, formX, formWidth);
        drawContentLabel(context, Text.translatable("dmls.field.player_limit.label"), formX, contentY(scaled(190)));
        renderCommandStatus(context, formX, formWidth);
        renderValidation(context, formWidth);
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPreview(DrawContext context, int formX, int formWidth) {
        int labelY = contentY(scaled(138));
        int previewY = contentY(scaled(152));
        drawContentLabel(context, Text.translatable("dmls.field.preview.label"), formX, labelY);
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
        int statusY = contentY(COMMAND_STATUS_OFFSET);
        int color = commandLength > PrefixCreateModule.MAX_COMMAND_LENGTH ? 0xFFFF5555 : 0xFFAAAAAA;
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.prefix.command_length", commandLength, PrefixCreateModule.MAX_COMMAND_LENGTH),
                    formX, statusY, color);
        }
    }

    private void renderValidation(DrawContext context, int formWidth) {
        if (validation.valid() || validation.message().getString().isEmpty()) {
            return;
        }
        int y = contentY(VALIDATION_OFFSET);
        List<OrderedText> lines = textRenderer.wrapLines(validation.message(), formWidth);
        for (OrderedText line : lines) {
            if (isContentVisible(y, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFFF5555);
            }
            y += VALIDATION_LINE_SPACING;
        }
    }

    private int contentHeight() {
        int contentBottom = COMMAND_STATUS_OFFSET + textRenderer.fontHeight;
        if (!validation.valid() && !validation.message().getString().isEmpty()) {
            int formWidth = Math.min(scaled(360), width - scaled(48));
            int lineCount = textRenderer.wrapLines(validation.message(), formWidth).size();
            contentBottom = VALIDATION_OFFSET
                    + Math.max(0, lineCount - 1) * VALIDATION_LINE_SPACING
                    + textRenderer.fontHeight;
        }
        return contentBottom + CONTENT_BOTTOM_PADDING;
    }

    private void drawContentLabel(DrawContext context, Text label, int x, int y) {
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, label, x, y, 0xFFCCCCCC);
        }
    }
}
