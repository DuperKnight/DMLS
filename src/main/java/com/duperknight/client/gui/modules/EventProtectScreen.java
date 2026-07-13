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
    private Text validationMessage = Text.empty();

    public EventProtectScreen(Screen parent, EventProtectModule module) {
        super(Text.translatable("dmls.module.event_protect.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(78));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        eventNameField = new TextFieldWidget(textRenderer, x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_protect.event_name"));
        // TextFieldWidget counts UTF-16 units; allow surrogate pairs and let
        // the module enforce the actual 64-code-point limit.
        eventNameField.setMaxLength(EventProtectModule.MAX_EVENT_NAME_CODE_POINTS * 2);
        eventNameField.setPlaceholder(Text.translatable("dmls.module.event_protect.event_name_placeholder"));
        eventNameField.setChangedListener(value -> validationMessage = Text.empty());
        addScrollableChild(eventNameField, 0);

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_protect.broadcast"), button -> broadcast())
                .dimensions(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(30));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void broadcast() {
        EventProtectModule.BroadcastResult result = module.broadcastProtection(client, eventNameField.getText());
        validationMessage = switch (result) {
            case INVALID -> Text.translatable("dmls.validation.event_protect.name");
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
        int validationY = contentY(scaled(60));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
