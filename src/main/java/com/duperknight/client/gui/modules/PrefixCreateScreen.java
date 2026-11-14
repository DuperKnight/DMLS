package com.duperknight.client.gui.modules;

import com.duperknight.client.utils.EditBoxUtils;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.utils.PrefixTextFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Form for creating a formatted server prefix. */
public final class PrefixCreateScreen extends DMLSMenuScreen {
    private static final int PREVIEW_HEIGHT = 24;
    private static final int COMMAND_STATUS_OFFSET = scaled(238);
    private static final int VALIDATION_OFFSET = scaled(258);
    private static final int VALIDATION_LINE_SPACING = scaled(12);
    private static final int CONTENT_BOTTOM_PADDING = scaled(10);

    private final PrefixCreateModule module;
    private EditBox ignField;
    private EditBox prefixIdField;
    private EditBox prefixTextField;
    private EditBox customLimitField;
    private String limit = PrefixCreateModule.LIMITS.get(0);
    private Button submitButton;
    private PrefixTextFormatter.ParseResult preview = PrefixTextFormatter.parse("");
    private PrefixCreateModule.ValidationResult validation = PrefixCreateModule.ValidationResult.success("");

    public PrefixCreateScreen(Screen parent, PrefixCreateModule module) {
        super(Component.translatable("dmls.module.prefix.name"), parent);
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

        ignField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setValue(savedIgn);
        ignField.setSuggestion(savedIgn.isEmpty() ? translated("dmls.placeholder.player_name") : null);
        ignField.setResponder(value -> {
            ignField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.player_name") : null);
            refreshValidation();
        });
        setInitialFocus(ignField);

        prefixIdField = addScrollableChild(new EditBox(font, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.prefix_id")), scaled(60));
        prefixIdField.setMaxLength(64);
        prefixIdField.setValue(savedPrefixId);
        prefixIdField.setSuggestion(savedPrefixId.isEmpty() ? translated("dmls.placeholder.prefix_id") : null);
        prefixIdField.setResponder(value -> {
            prefixIdField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.prefix_id") : null);
            refreshValidation();
        });

        prefixTextField = addScrollableChild(new EditBox(font, formX, contentY(scaled(106)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.prefix_text")), scaled(106));
        prefixTextField.setMaxLength(PrefixCreateModule.MAX_COMMAND_LENGTH);
        prefixTextField.setValue(savedPrefixText);
        prefixTextField.setSuggestion(savedPrefixText.isEmpty() ? translated("dmls.placeholder.prefix_text") : null);
        prefixTextField.setResponder(value -> {
            prefixTextField.setSuggestion(value.isEmpty() ? translated("dmls.placeholder.prefix_text") : null);
            refreshValidation();
        });

        addScrollableDropdownChild(DropdownWidget.builder(
                        Component.translatable("dmls.field.player_limit"), PrefixCreateModule.LIMITS, limit,
                        value -> PrefixCreateModule.CUSTOM_LIMIT.equals(value)
                                ? Component.translatable("dmls.field.custom") : Component.literal(value),
                        (dropdown, value) -> {
                            limit = value;
                            updateCustomLimitState();
                            refreshValidation();
                        })
                .dimensions(formX, contentY(scaled(204)), splitWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), scaled(204));

        customLimitField = addScrollableChild(new EditBox(font, formX + splitWidth + scaled(4),
                contentY(scaled(204)), customWidth, STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.custom")), scaled(204));
        customLimitField.setMaxLength(10);
        EditBoxUtils.setFilter(customLimitField, value -> value.isEmpty() || value.chars().allMatch(character -> character >= '0' && character <= '9'));
        customLimitField.setValue(savedCustomLimit);
        customLimitField.setSuggestion(savedCustomLimit.isEmpty() ? translated("dmls.field.custom") : null);
        customLimitField.setResponder(value -> {
            customLimitField.setSuggestion(value.isEmpty() ? translated("dmls.field.custom") : null);
            refreshValidation();
        });
        updateCustomLimitState();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.create"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()),
                () -> validation.valid());
        refreshValidation();
    }

    private static String fieldText(EditBox field) {
        return field == null ? "" : field.getValue();
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
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
            customLimitField.moveCursorToEnd(false);
        }
    }

    private String selectedLimit() {
        return PrefixCreateModule.CUSTOM_LIMIT.equals(limit) ? customLimitField.getValue().trim() : limit;
    }

    private void refreshValidation() {
        if (ignField == null || prefixIdField == null || prefixTextField == null || customLimitField == null) {
            return;
        }
        preview = PrefixTextFormatter.parse(prefixTextField.getValue());
        validation = PrefixCreateModule.validate(ignField.getValue().trim(), selectedLimit(), prefixIdField.getValue().trim(), prefixTextField.getValue());
        updateScrollableContentHeight(contentHeight());
    }

    private void submit() {
        PrefixCreateModule.ValidationResult result = module.submit(minecraft, ignField.getValue().trim(), selectedLimit(),
                prefixIdField.getValue().trim(), prefixTextField.getValue());
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        drawContentLabel(context, Component.translatable("dmls.field.player_ign.label"), ignField.getX(), contentY(0));
        drawContentLabel(context, Component.translatable("dmls.field.prefix_id.label"), prefixIdField.getX(), contentY(scaled(46)));
        drawContentLabel(context, Component.translatable("dmls.field.prefix_text.label"), prefixTextField.getX(), contentY(scaled(92)));
        renderPreview(context, formX, formWidth);
        drawContentLabel(context, Component.translatable("dmls.field.player_limit.label"), formX, contentY(scaled(190)));
        renderCommandStatus(context, formX, formWidth);
        renderValidation(context, formWidth);
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderPreview(GuiGraphicsExtractor context, int formX, int formWidth) {
        int labelY = contentY(scaled(138));
        int previewY = contentY(scaled(152));
        drawContentLabel(context, Component.translatable("dmls.field.preview.label"), formX, labelY);
        if (!isContentVisible(previewY, PREVIEW_HEIGHT)) {
            return;
        }
        renderPanel(context, formX, previewY, formWidth, PREVIEW_HEIGHT);
        if (preview.valid()) {
            context.enableScissor(formX + scaled(3), previewY + scaled(3), formX + formWidth - scaled(3), previewY + PREVIEW_HEIGHT - scaled(3));
            int previewTextY = previewY + (PREVIEW_HEIGHT - font.lineHeight) / 2 + 1;
            context.text(font, preview.preview(), formX + scaled(6), previewTextY, 0xFFFFFFFF);
            context.disableScissor();
        }
    }

    private void renderCommandStatus(GuiGraphicsExtractor context, int formX, int formWidth) {
        int commandLength = PrefixCreateModule.createCommand(prefixIdField.getValue().trim(), prefixTextField.getValue()).length();
        int statusY = contentY(COMMAND_STATUS_OFFSET);
        int color = commandLength > PrefixCreateModule.MAX_COMMAND_LENGTH ? 0xFFFF5555 : 0xFFAAAAAA;
        if (isContentVisible(statusY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.prefix.command_length", commandLength, PrefixCreateModule.MAX_COMMAND_LENGTH),
                    formX, statusY, color);
        }
    }

    private void renderValidation(GuiGraphicsExtractor context, int formWidth) {
        if (validation.valid() || validation.message().getString().isEmpty()) {
            return;
        }
        int y = contentY(VALIDATION_OFFSET);
        List<FormattedCharSequence> lines = font.split(validation.message(), formWidth);
        for (FormattedCharSequence line : lines) {
            if (isContentVisible(y, font.lineHeight)) {
                context.centeredText(font, line, width / 2, y, 0xFFFF5555);
            }
            y += VALIDATION_LINE_SPACING;
        }
    }

    private int contentHeight() {
        int contentBottom = COMMAND_STATUS_OFFSET + font.lineHeight;
        if (!validation.valid() && !validation.message().getString().isEmpty()) {
            int formWidth = Math.min(scaled(360), width - scaled(48));
            int lineCount = font.split(validation.message(), formWidth).size();
            contentBottom = VALIDATION_OFFSET
                    + Math.max(0, lineCount - 1) * VALIDATION_LINE_SPACING
                    + font.lineHeight;
        }
        return contentBottom + CONTENT_BOTTOM_PADDING;
    }

    private void drawContentLabel(GuiGraphicsExtractor context, Component label, int x, int y) {
        if (isContentVisible(y, font.lineHeight)) {
            context.text(font, label, x, y, 0xFFCCCCCC);
        }
    }
}
