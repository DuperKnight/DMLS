package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.RemoveButtonWidget;
import com.duperknight.client.modules.GreeterMessages;
import com.duperknight.client.modules.GreeterModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Settings screen for the new player greeter. */
public final class GreeterScreen extends DMLSMenuScreen {
    private static final int MESSAGE_START_UNSCALED = 34;
    private static final int MESSAGE_ROW_HEIGHT_UNSCALED = 42;
    private static final int HINT_FIRST_LINE_GAP_UNSCALED = 31;
    private static final int HINT_LINE_SPACING_UNSCALED = 13;

    private final GreeterModule module;
    private final List<String> messages;
    private final List<TextFieldWidget> messageFields = new ArrayList<>();
    private Text saveStatus = Text.empty();
    private int saveStatusColor = 0xFFFF5555;

    public GreeterScreen(Screen parent, GreeterModule module) {
        super(Text.translatable("dmls.module.greeter.name"), parent);
        this.module = module;
        this.messages = new ArrayList<>(module.customMessages());
        if (messages.isEmpty()) {
            messages.add("");
        }
    }

    @Override
    protected void init() {
        messageFields.clear();
        int addButtonOffset = scaled(MESSAGE_START_UNSCALED
                + messages.size() * MESSAGE_ROW_HEIGHT_UNSCALED);
        configureScrollableContent(module, addButtonOffset
                + scaled(HINT_FIRST_LINE_GAP_UNSCALED + HINT_LINE_SPACING_UNSCALED + 10));

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), module.enabled()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.greeter.toggle"),
                        (button, value) -> {
                            if (module.setEnabled(client, value)) {
                                saveStatus = Text.empty();
                            } else {
                                button.setValue(module.enabled());
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                                saveStatusColor = 0xFFFF5555;
                            }
                        }), 0);

        int removeWidth = scaled(20);
        int gap = scaled(4);
        boolean removable = messages.size() > 1;
        for (int index = 0; index < messages.size(); index++) {
            int messageIndex = index;
            int rowOffset = scaled(MESSAGE_START_UNSCALED
                    + index * MESSAGE_ROW_HEIGHT_UNSCALED);
            int fieldOffset = rowOffset + scaled(13);
            int fieldWidth = removable ? formWidth - removeWidth - gap : formWidth;
            TextFieldWidget field = addScrollableChild(new TextFieldWidget(
                    textRenderer, formX, contentY(fieldOffset), fieldWidth, STANDARD_BUTTON_HEIGHT,
                    Text.translatable("dmls.module.greeter.message_field", index + 1)), fieldOffset);
            field.setMaxLength(GreeterMessages.MAX_TEMPLATE_LENGTH);
            field.setText(messages.get(index));
            updateSuggestion(field);
            field.setChangedListener(value -> {
                messages.set(messageIndex, value);
                updateSuggestion(field);
                saveStatus = Text.empty();
            });
            messageFields.add(field);

            if (removable) {
                addScrollableChild(new RemoveButtonWidget(
                        formX + formWidth - removeWidth, contentY(fieldOffset),
                        removeWidth, STANDARD_BUTTON_HEIGHT,
                        button -> removeMessage(messageIndex)), fieldOffset);
            }
        }

        addScrollableChild(ButtonWidget.builder(
                        Text.translatable("dmls.module.greeter.add_message"), button -> addMessage())
                .dimensions(formX, contentY(addButtonOffset), formWidth, STANDARD_BUTTON_HEIGHT).build(),
                addButtonOffset);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.button.save"), button -> saveMessages())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
    }

    private void addMessage() {
        messages.add("");
        saveStatus = Text.empty();
        clearAndInit();
        scrollContentToBottom();
        if (!messageFields.isEmpty()) {
            setFocused(messageFields.get(messageFields.size() - 1));
        }
    }

    private void removeMessage(int index) {
        if (messages.size() <= 1) return;
        messages.remove(index);
        saveStatus = Text.empty();
        clearAndInit();
    }

    private void saveMessages() {
        if (GreeterMessages.normalizeTemplates(messages).isEmpty()) {
            saveStatus = Text.translatable("dmls.validation.greeter.message");
            saveStatusColor = 0xFFFF5555;
            return;
        }
        if (module.setCustomMessages(messages)) {
            saveStatus = Text.translatable("dmls.module.greeter.messages_saved");
            saveStatusColor = 0xFF55FF55;
        } else {
            saveStatus = Text.translatable("dmls.validation.config.save_failed");
            saveStatusColor = 0xFFFF5555;
        }
    }

    private void updateSuggestion(TextFieldWidget field) {
        field.setSuggestion(field.getText().isEmpty()
                ? Text.translatable("dmls.module.greeter.message_placeholder").getString()
                : null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        for (int index = 0; index < messages.size(); index++) {
            int labelY = contentY(scaled(MESSAGE_START_UNSCALED
                    + index * MESSAGE_ROW_HEIGHT_UNSCALED));
            if (isContentVisible(labelY, textRenderer.fontHeight)) {
                context.drawTextWithShadow(textRenderer,
                        Text.translatable("dmls.module.greeter.message_field", index + 1),
                        formX, labelY, 0xFFCCCCCC);
            }
        }
        int addButtonOffset = scaled(MESSAGE_START_UNSCALED
                + messages.size() * MESSAGE_ROW_HEIGHT_UNSCALED);
        int hintY = contentY(addButtonOffset + scaled(HINT_FIRST_LINE_GAP_UNSCALED));
        Text variableHint = Text.translatable("dmls.module.greeter.variable_hint",
                GreeterMessages.PLAYER_VARIABLE);
        if (isContentVisible(hintY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, variableHint,
                    width / 2, hintY, 0xFFAAAAAA);
        }
        int fallbackY = hintY + scaled(HINT_LINE_SPACING_UNSCALED);
        if (isContentVisible(fallbackY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("dmls.module.greeter.default_hint"),
                    width / 2, fallbackY, 0xFFAAAAAA);
        }
        endContentScissor(context);

        if (!saveStatus.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, saveStatus, width / 2,
                    footerButtonY() - scaled(13), saveStatusColor);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
