package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventPowerToolModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

/** Standard DMLS form and saved-row list for held-item PowerTool bindings. */
public final class EventPowerToolScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 24;
    private static final int SAVED_HEADING_OFFSET_UNSCALED = 128;
    private static final int SAVED_ROWS_OFFSET_UNSCALED = 148;

    private final EventPowerToolModule module;
    private TextFieldWidget nameField;
    private TextFieldWidget commandField;
    private ButtonWidget saveButton;
    private Text validation = Text.empty();
    private Text status = Text.empty();

    public EventPowerToolScreen(Screen parent, EventPowerToolModule module) {
        super(Text.translatable("dmls.module.event_powertool.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedName = fieldText(nameField);
        String savedCommand = fieldText(commandField);
        Map<String, String> entries = module.saved();

        int rowsOffset = scaled(SAVED_ROWS_OFFSET_UNSCALED);
        int contentHeight = Math.max(scaled(174), rowsOffset
                + entries.size() * scaled(ROW_HEIGHT_UNSCALED) + scaled(8));
        configureScrollableContent(module, contentHeight);

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        nameField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_powertool.name_field")), scaled(14));
        nameField.setMaxLength(EventPowerToolModule.MAX_NAME_LENGTH);
        nameField.setText(savedName);
        updateNameSuggestion();
        nameField.setChangedListener(value -> {
            updateNameSuggestion();
            status = Text.empty();
            refreshValidation();
        });
        setInitialFocus(nameField);

        commandField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_powertool.command_field")), scaled(60));
        commandField.setMaxLength(EventPowerToolModule.MAX_COMMAND_LENGTH + 1);
        commandField.setText(savedCommand);
        updateCommandSuggestion();
        commandField.setChangedListener(value -> {
            updateCommandSuggestion();
            status = Text.empty();
            refreshValidation();
        });

        registerCommandControl(addScrollableChild(ButtonWidget.builder(
                Text.translatable("dmls.button.event_powertool.clear"), button -> {
            status = Text.empty();
            if (module.clear(client)) {
                closeToGame();
            } else {
                status = Text.translatable("dmls.validation.event_powertool.action_blocked");
            }
        }).dimensions(formX, contentY(scaled(92)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(92)));

        int deleteWidth = scaled(20);
        int gap = scaled(4);
        int row = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String name = entry.getKey();
            String command = entry.getValue();
            int offset = rowsOffset + row * scaled(ROW_HEIGHT_UNSCALED);
            Text rowText = Text.literal(name + " ").append(Text.literal("/" + command).formatted(Formatting.DARK_GRAY));
            registerCommandControl(addScrollableChild(ButtonWidget.builder(rowText, button -> {
                        status = Text.empty();
                        if (module.load(client, name)) {
                            closeToGame();
                        } else {
                            status = Text.translatable("dmls.validation.event_powertool.action_blocked");
                        }
                    })
                    .dimensions(formX, contentY(offset), formWidth - deleteWidth - gap,
                            STANDARD_BUTTON_HEIGHT).build(), offset));
            addScrollableChild(ButtonWidget.builder(Text.literal("✕"), button -> {
                        status = Text.empty();
                        if (module.delete(client, name)) {
                            clearAndInit();
                        } else {
                            status = Text.translatable("dmls.validation.config.save_failed");
                        }
                    })
                    .dimensions(formX + formWidth - deleteWidth, contentY(offset), deleteWidth,
                            STANDARD_BUTTON_HEIGHT).build(), offset);
            row++;
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.save"), button -> save())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        refreshValidation();
    }

    private void save() {
        refreshValidation();
        if (!validation.getString().isEmpty()) return;
        if (module.save(client, nameField.getText(), commandField.getText())) {
            nameField.setText("");
            commandField.setText("");
            status = Text.empty();
            clearAndInit();
        } else {
            status = Text.translatable("dmls.validation.config.save_failed");
        }
    }

    private void refreshValidation() {
        if (nameField == null || commandField == null) return;
        String name = nameField.getText();
        String command = commandField.getText();
        boolean bothEmpty = name.isBlank() && command.isBlank();
        if (bothEmpty) {
            validation = Text.empty();
        } else if (name.isBlank() || command.isBlank()) {
            validation = Text.translatable("dmls.validation.event_powertool.incomplete");
        } else if (EventPowerToolModule.normalizeName(name).isEmpty()) {
            validation = Text.translatable("dmls.validation.event_powertool.name");
        } else if (EventPowerToolModule.normalizeCommand(command).isEmpty()) {
            validation = Text.translatable("dmls.validation.event_powertool.command");
        } else {
            validation = Text.empty();
        }
        if (saveButton != null) {
            saveButton.active = !bothEmpty && validation.getString().isEmpty();
        }
    }

    private void updateNameSuggestion() {
        nameField.setSuggestion(nameField.getText().isEmpty()
                ? Text.translatable("dmls.module.event_powertool.name_placeholder").getString() : null);
    }

    private void updateCommandSuggestion() {
        commandField.setSuggestion(commandField.getText().isEmpty()
                ? Text.translatable("dmls.module.event_powertool.command_placeholder").getString() : null);
    }

    private static String fieldText(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        drawLabel(context, Text.translatable("dmls.module.event_powertool.name_field"),
                formX, contentY(0));
        drawLabel(context, Text.translatable("dmls.module.event_powertool.command_field"),
                formX, contentY(scaled(46)));
        drawLabel(context, Text.translatable("dmls.screen.event_powertool.saved"),
                formX, contentY(scaled(SAVED_HEADING_OFFSET_UNSCALED)));

        if (module.saved().isEmpty()) {
            int emptyY = contentY(scaled(SAVED_ROWS_OFFSET_UNSCALED + 5));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("dmls.screen.event_powertool.empty"), width / 2, emptyY, 0xFFAAAAAA);
            }
        }
        endContentScissor(context);

        Text footerStatus = status.getString().isEmpty() ? validation : status;
        if (!footerStatus.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, footerStatus, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLabel(DrawContext context, Text label, int x, int y) {
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, label, x, y, 0xFFCCCCCC);
        }
    }
}
