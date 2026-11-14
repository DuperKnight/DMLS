package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ChatSpamMuteModule;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings screen for the client-side chat spam filters. */
public final class ChatSpamMuteScreen extends DMLSMenuScreen {
    private final ChatSpamMuteModule module;
    private Component saveStatus = Component.empty();

    public ChatSpamMuteScreen(Screen parent, ChatSpamMuteModule module) {
        super(Component.translatable("dmls.module.chat_spam.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(104));
        int controlWidth = Math.min(scaled(240), width - scaled(48));
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CycleButton.builder((Boolean value) -> Component.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED), DMLSConfig.tradeChatMuted()).withValues(true, false)
                .create(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.chat_spam.trade_chat_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setTradeChatMuted(value)) {
                                button.setValue(DMLSConfig.tradeChatMuted());
                                saveStatus = Component.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Component.empty();
                            }
                        }), 0);
        addScrollableChild(CycleButton.builder((Boolean value) -> Component.translatable(value ? "dmls.option.on" : "dmls.option.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED), DMLSConfig.serverMessagesMuted()).withValues(true, false)
                .create(x, contentY(scaled(30)), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Component.translatable("dmls.module.chat_spam.server_messages_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setServerMessagesMuted(value)) {
                                button.setValue(DMLSConfig.serverMessagesMuted());
                                saveStatus = Component.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Component.empty();
                            }
                        }), scaled(30));
        addScrollableChild(CycleButton.builder((Boolean value) -> Component.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED),
                        DMLSConfig.staffCommandFeedbackMuted()).withValues(true, false)
                .create(x, contentY(scaled(60)), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Component.translatable("dmls.module.chat_spam.staff_command_feedback_toggle"),
                        (button, value) -> {
                            if (!DMLSConfig.setStaffCommandFeedbackMuted(value)) {
                                button.setValue(DMLSConfig.staffCommandFeedbackMuted());
                                saveStatus = Component.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Component.empty();
                            }
                        }), scaled(60));
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(90));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
