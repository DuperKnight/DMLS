package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.utils.AlertWordlist;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings local to the Chat Alerts module. */
public final class ChatAlertsScreen extends DMLSMenuScreen {
    private final ChatAlertsModule module;
    private Text wordlistStatus;

    public ChatAlertsScreen(Screen parent, ChatAlertsModule module) {
        super(Text.translatable("dmls.module.chat_alerts.name"), parent);
        this.module = module;
        wordlistStatus = wordlistStatusText();
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(78));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), DMLSConfig.alertsEnabled()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.chat_alerts.toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setAlertsEnabled(value)) {
                                button.setValue(DMLSConfig.alertsEnabled());
                                wordlistStatus = Text.translatable("dmls.validation.config.save_failed");
                            } else {
                                wordlistStatus = wordlistStatusText();
                            }
                        }), 0);
        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.chat_alerts.reload"), button -> {
            wordlistStatus = wordlistStatusText(ChatAlertsModule.reloadWordlistResult());
        }).dimensions(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(30));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(62));
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, wordlistStatus, width / 2, statusY, 0xFFAAAAAA);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private Text wordlistStatusText() {
        return wordlistStatusText(ChatAlertsModule.lastWordlistLoadResult());
    }

    private Text wordlistStatusText(AlertWordlist.LoadResult result) {
        int count = result.wordCount();
        if (result.status() == AlertWordlist.LoadStatus.FAILED) {
            return Text.translatable("dmls.module.chat_alerts.reload_failed", count);
        }
        return Text.translatable(count == 1 ? "dmls.module.chat_alerts.words.one" : "dmls.module.chat_alerts.words.many", count);
    }
}
