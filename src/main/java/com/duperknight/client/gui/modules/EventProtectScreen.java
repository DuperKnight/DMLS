package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventProtectModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings local to the Event Protect module. */
public final class EventProtectScreen extends DMLSMenuScreen {
    private final EventProtectModule module;
    private EditBox eventNameField;
    private EditBox landNameField;
    private Button broadcastButton;
    private Component validationMessage = Component.empty();

    public EventProtectScreen(Screen parent, EventProtectModule module) {
        super(Component.translatable("dmls.module.event_protect.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(110));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = width / 2 - formWidth / 2;

        eventNameField = new EditBox(font, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_protect.event_name"));
        // TextFieldWidget counts UTF-16 units; allow surrogate pairs and let
        // the module enforce the actual 64-code-point limit.
        eventNameField.setMaxLength(EventProtectModule.MAX_EVENT_NAME_CODE_POINTS * 2);
        String eventSuggestion = Component.translatable("dmls.module.event_protect.event_name_placeholder").getString();
        eventNameField.setSuggestion(eventSuggestion);
        eventNameField.setResponder(value -> {
            eventNameField.setSuggestion(value.isEmpty() ? eventSuggestion : null);
            validationMessage = Component.empty();
        });
        addScrollableChild(eventNameField, scaled(14));
        setInitialFocus(eventNameField);

        landNameField = new EditBox(font, formX, contentY(scaled(60)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.module.event_protect.land_name"));
        landNameField.setMaxLength(EventProtectModule.MAX_LAND_NAME_CODE_POINTS * 2);
        String landSuggestion = Component.translatable("dmls.module.event_protect.land_name_placeholder").getString();
        landNameField.setSuggestion(landSuggestion);
        landNameField.setResponder(value -> {
            landNameField.setSuggestion(value.isEmpty() ? landSuggestion : null);
            validationMessage = Component.empty();
        });
        addScrollableChild(landNameField, scaled(60));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        broadcastButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.module.event_protect.broadcast"), button -> broadcast())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void broadcast() {
        EventProtectModule.BroadcastResult result = module.broadcastProtection(
                minecraft, eventNameField.getValue(), landNameField.getValue());
        validationMessage = switch (result) {
            case INVALID_EVENT -> Component.translatable("dmls.validation.event_protect.name");
            case INVALID_LAND -> Component.translatable("dmls.validation.event_protect.land");
            case RANK_BLOCKED -> Component.translatable("dmls.validation.required_rank");
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.server_blocked");
            case SENT, SIMULATED -> Component.empty();
        };
        if (result == EventProtectModule.BroadcastResult.SENT
                || result == EventProtectModule.BroadcastResult.SIMULATED) {
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, font.lineHeight)) {
            context.text(font,
                    Component.translatable("dmls.module.event_protect.event_name.label"),
                    eventNameField.getX(), labelY, 0xFFCCCCCC);
        }
        int landLabelY = contentY(scaled(46));
        if (isContentVisible(landLabelY, font.lineHeight)) {
            context.text(font,
                    Component.translatable("dmls.module.event_protect.land_name.label"),
                    landNameField.getX(), landLabelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(94));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
