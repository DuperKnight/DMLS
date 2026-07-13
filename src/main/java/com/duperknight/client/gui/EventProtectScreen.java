package com.duperknight.client.gui;

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
        eventNameField.setMaxLength(64);
        eventNameField.setPlaceholder(Text.translatable("dmls.module.event_protect.event_name_placeholder"));
        addScrollableChild(eventNameField, 0);

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_protect.broadcast"), button -> {
            module.setEventName(eventNameField.getText());
            module.broadcastProtection();
            close();
        }).dimensions(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(30));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        super.render(context, mouseX, mouseY, delta);
    }
}