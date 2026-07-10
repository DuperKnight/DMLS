package com.duperknight.client.gui;

import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Settings local to the Chat Alerts module. */
public final class ChatAlertsScreen extends DMLSMenuScreen {
    private final ChatAlertsModule module;
    private Text wordlistStatus;

    public ChatAlertsScreen(Screen parent, ChatAlertsModule module) {
        super(Text.literal("Chat Alerts"), parent);
        this.module = module;
        wordlistStatus = wordlistStatusText();
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(78));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.onOffBuilder(DMLSConfig.alertsEnabled())
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.literal("Chat Alerts"),
                        (button, value) -> DMLSConfig.setAlertsEnabled(value)), 0);
        addScrollableChild(ButtonWidget.builder(Text.literal("Reload Alert Wordlist"), button -> {
            ChatAlertsModule.reloadWordlist();
            wordlistStatus = wordlistStatusText();
        }).dimensions(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(30));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        int statusY = contentY(scaled(62));
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, wordlistStatus, width / 2, statusY, 0xFFAAAAAA);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private Text wordlistStatusText() {
        int count = ChatAlertsModule.wordCount();
        return Text.literal(count + " alert word" + (count == 1 ? "" : "s") + " loaded (config/dmls-alerts.txt)");
    }
}
