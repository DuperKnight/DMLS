package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ChatSpamMuteModule;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings screen for the client-side chat spam filters. */
public final class ChatSpamMuteScreen extends DMLSMenuScreen {
    private final ChatSpamMuteModule module;
    private Text saveStatus = Text.empty();

    public ChatSpamMuteScreen(Screen parent, ChatSpamMuteModule module) {
        super(Text.translatable("dmls.module.chat_spam.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(104));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), DMLSConfig.tradeChatMuted()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.chat_spam.trade_chat_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setTradeChatMuted(value)) {
                                button.setValue(DMLSConfig.tradeChatMuted());
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Text.empty();
                            }
                        }), 0);
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), DMLSConfig.serverMessagesMuted()).values(true, false)
                .build(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable("dmls.module.chat_spam.server_messages_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setServerMessagesMuted(value)) {
                                button.setValue(DMLSConfig.serverMessagesMuted());
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Text.empty();
                            }
                        }), scaled(30));
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED),
                        DMLSConfig.serverSummonMessagesMuted()).values(true, false)
                .build(x, contentY(scaled(60)), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable("dmls.module.chat_spam.server_summon_messages_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setServerSummonMessagesMuted(value)) {
                                button.setValue(DMLSConfig.serverSummonMessagesMuted());
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Text.empty();
                            }
                        }), scaled(60));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(90));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
