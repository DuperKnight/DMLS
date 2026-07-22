package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventProtectModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Settings local to the Event Protect module. */
public final class EventProtectScreen extends DMLSMenuScreen {
    private final EventProtectModule module;
    private TextFieldWidget eventNameField;
    private TextFieldWidget landNameField;
    private ButtonWidget broadcastButton;
    private Text validationMessage = Text.empty();

    public EventProtectScreen(Screen parent, EventProtectModule module) {
        super(Text.translatable("dmls.module.event_protect.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(110));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = width / 2 - formWidth / 2;

        eventNameField = new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_protect.event_name"));
        // TextFieldWidget counts UTF-16 units; allow surrogate pairs and let
        // the module enforce the actual 64-code-point limit.
        eventNameField.setMaxLength(EventProtectModule.MAX_EVENT_NAME_CODE_POINTS * 2);
        String eventSuggestion = Text.translatable("dmls.module.event_protect.event_name_placeholder").getString();
        eventNameField.setSuggestion(eventSuggestion);
        eventNameField.setChangedListener(value -> {
            eventNameField.setSuggestion(value.isEmpty() ? eventSuggestion : null);
            validationMessage = Text.empty();
        });
        addScrollableChild(eventNameField, scaled(14));
        setInitialFocus(eventNameField);

        landNameField = new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_protect.land_name"));
        landNameField.setMaxLength(EventProtectModule.MAX_LAND_NAME_CODE_POINTS * 2);
        String landSuggestion = Text.translatable("dmls.module.event_protect.land_name_placeholder").getString();
        landNameField.setSuggestion(landSuggestion);
        landNameField.setChangedListener(value -> {
            landNameField.setSuggestion(value.isEmpty() ? landSuggestion : null);
            validationMessage = Text.empty();
        });
        addScrollableChild(landNameField, scaled(60));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        broadcastButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.module.event_protect.broadcast"), button -> broadcast())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void broadcast() {
        EventProtectModule.BroadcastResult result = module.broadcastProtection(
                client, eventNameField.getText(), landNameField.getText());
        validationMessage = switch (result) {
            case INVALID_EVENT -> Text.translatable("dmls.validation.event_protect.name");
            case INVALID_LAND -> Text.translatable("dmls.validation.event_protect.land");
            case RANK_BLOCKED -> Text.translatable("dmls.validation.required_rank");
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.server_blocked");
            case SENT, SIMULATED -> Text.empty();
        };
        if (result == EventProtectModule.BroadcastResult.SENT
                || result == EventProtectModule.BroadcastResult.SIMULATED) {
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer,
                    Text.translatable("dmls.module.event_protect.event_name.label"),
                    eventNameField.getX(), labelY, 0xFFCCCCCC);
        }
        int landLabelY = contentY(scaled(46));
        if (isContentVisible(landLabelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer,
                    Text.translatable("dmls.module.event_protect.land_name.label"),
                    landNameField.getX(), landLabelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(94));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
