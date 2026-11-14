package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.RemoveButtonWidget;
import com.duperknight.client.modules.EventPowerToolModule;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Standard DMLS form and saved-row list for held-item PowerTool bindings. */
public final class EventPowerToolScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 24;
    private static final int SAVED_HEADING_OFFSET_UNSCALED = 128;
    private static final int SAVED_ROWS_OFFSET_UNSCALED = 148;

    private final EventPowerToolModule module;
    private EditBox nameField;
    private EditBox commandField;
    private Button saveButton;
    private Component validation = Component.empty();
    private Component status = Component.empty();

    public EventPowerToolScreen(Screen parent, EventPowerToolModule module) {
        super(Component.translatable("dmls.module.event_powertool.name"), parent);
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

        nameField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_powertool.name_field")), scaled(14));
        nameField.setMaxLength(EventPowerToolModule.MAX_NAME_LENGTH);
        nameField.setValue(savedName);
        updateNameSuggestion();
        nameField.setResponder(value -> {
            updateNameSuggestion();
            status = Component.empty();
            refreshValidation();
        });
        setInitialFocus(nameField);

        commandField = addScrollableChild(new EditBox(font, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_powertool.command_field")), scaled(60));
        commandField.setMaxLength(EventPowerToolModule.MAX_COMMAND_LENGTH + 1);
        commandField.setValue(savedCommand);
        updateCommandSuggestion();
        commandField.setResponder(value -> {
            updateCommandSuggestion();
            status = Component.empty();
            refreshValidation();
        });

        registerCommandControl(addScrollableChild(Button.builder(
                Component.translatable("dmls.button.event_powertool.clear"), button -> {
            status = Component.empty();
            if (module.clear(minecraft)) {
                closeToGame();
            } else {
                status = Component.translatable("dmls.validation.event_powertool.action_blocked");
            }
        }).bounds(formX, contentY(scaled(92)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(92)));

        int deleteWidth = scaled(20);
        int gap = scaled(4);
        int row = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String name = entry.getKey();
            String command = entry.getValue();
            int offset = rowsOffset + row * scaled(ROW_HEIGHT_UNSCALED);
            Component rowText = Component.literal(name + " ").append(Component.literal("/" + command).withStyle(ChatFormatting.DARK_GRAY));
            registerCommandControl(addScrollableChild(Button.builder(rowText, button -> {
                        status = Component.empty();
                        if (module.load(minecraft, name)) {
                            closeToGame();
                        } else {
                            status = Component.translatable("dmls.validation.event_powertool.action_blocked");
                        }
                    })
                    .bounds(formX, contentY(offset), formWidth - deleteWidth - gap,
                            STANDARD_BUTTON_HEIGHT).build(), offset));
            addScrollableChild(new RemoveButtonWidget(
                    formX + formWidth - deleteWidth, contentY(offset),
                    deleteWidth, STANDARD_BUTTON_HEIGHT,
                    button -> {
                        status = Component.empty();
                        if (module.delete(minecraft, name)) {
                            rebuildWidgets();
                        } else {
                            status = Component.translatable("dmls.validation.config.save_failed");
                        }
                    }), offset);
            row++;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("dmls.button.save"), button -> save())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        refreshValidation();
    }

    private void save() {
        refreshValidation();
        if (!validation.getString().isEmpty()) return;
        if (module.save(minecraft, nameField.getValue(), commandField.getValue())) {
            nameField.setValue("");
            commandField.setValue("");
            status = Component.empty();
            rebuildWidgets();
        } else {
            status = Component.translatable("dmls.validation.config.save_failed");
        }
    }

    private void refreshValidation() {
        if (nameField == null || commandField == null) return;
        String name = nameField.getValue();
        String command = commandField.getValue();
        boolean bothEmpty = name.isBlank() && command.isBlank();
        if (bothEmpty) {
            validation = Component.empty();
        } else if (name.isBlank() || command.isBlank()) {
            validation = Component.translatable("dmls.validation.event_powertool.incomplete");
        } else if (EventPowerToolModule.normalizeName(name).isEmpty()) {
            validation = Component.translatable("dmls.validation.event_powertool.name");
        } else if (EventPowerToolModule.normalizeCommand(command).isEmpty()) {
            validation = Component.translatable("dmls.validation.event_powertool.command");
        } else {
            validation = Component.empty();
        }
        if (saveButton != null) {
            saveButton.active = !bothEmpty && validation.getString().isEmpty();
        }
    }

    private void updateNameSuggestion() {
        nameField.setSuggestion(nameField.getValue().isEmpty()
                ? Component.translatable("dmls.module.event_powertool.name_placeholder").getString() : null);
    }

    private void updateCommandSuggestion() {
        commandField.setSuggestion(commandField.getValue().isEmpty()
                ? Component.translatable("dmls.module.event_powertool.command_placeholder").getString() : null);
    }

    private static String fieldText(EditBox field) {
        return field == null ? "" : field.getValue();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        drawLabel(context, Component.translatable("dmls.module.event_powertool.name_field"),
                formX, contentY(0));
        drawLabel(context, Component.translatable("dmls.module.event_powertool.command_field"),
                formX, contentY(scaled(46)));
        drawLabel(context, Component.translatable("dmls.screen.event_powertool.saved"),
                formX, contentY(scaled(SAVED_HEADING_OFFSET_UNSCALED)));

        if (module.saved().isEmpty()) {
            int emptyY = contentY(scaled(SAVED_ROWS_OFFSET_UNSCALED + 5));
            if (isContentVisible(emptyY, font.lineHeight)) {
                context.centeredText(font,
                        Component.translatable("dmls.screen.event_powertool.empty"), width / 2, emptyY, 0xFFAAAAAA);
            }
        }
        endContentScissor(context);

        Component footerStatus = status.getString().isEmpty() ? validation : status;
        if (!footerStatus.getString().isEmpty()) {
            context.centeredText(font, footerStatus, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphicsExtractor context, Component label, int x, int y) {
        if (isContentVisible(y, font.lineHeight)) {
            context.text(font, label, x, y, 0xFFCCCCCC);
        }
    }
}
