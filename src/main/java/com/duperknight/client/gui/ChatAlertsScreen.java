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
        int x = width / 2 - 100;
        int y = height / 2 - 40;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(DMLSConfig.alertsEnabled())
                .build(x, y, 200, 20, Text.literal("Chat Alerts"), (button, value) -> DMLSConfig.setAlertsEnabled(value)));
        addDrawableChild(ButtonWidget.builder(Text.literal("Reload Alert Wordlist"), button -> {
            ChatAlertsModule.reloadWordlist();
            wordlistStatus = wordlistStatusText();
        }).dimensions(x, y + 28, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - 75, height - 31, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        context.drawCenteredTextWithShadow(textRenderer, wordlistStatus, width / 2, height / 2 + 55, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    private Text wordlistStatusText() {
        int count = ChatAlertsModule.wordCount();
        return Text.literal(count + " alert word" + (count == 1 ? "" : "s") + " loaded (config/dmls-alerts.txt)");
    }
}
