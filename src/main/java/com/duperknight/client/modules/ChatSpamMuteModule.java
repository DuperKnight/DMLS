package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ChatSpamMuteScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/** Client-side filter for repetitive trade chat and server messages. */
public final class ChatSpamMuteModule extends DMLSModule {
    public ChatSpamMuteModule() {
        super(StaffRank.ADMIN);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.chat_spam.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BARRIER);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("dmls.module.chat_spam.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new ChatSpamMuteScreen(parent, this));
    }

    @Override
    public void register() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> !shouldHide(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !shouldHide(message));
    }

    private boolean shouldHide(Component message) {
        return shouldHideForCurrentSettings(ChatUtils.cleanLine(message.getString()));
    }

    /** Applies the live module settings to another client-side chat surface. */
    public static boolean shouldHideForCurrentSettings(String cleanMessage) {
        return shouldHide(
                cleanMessage,
                DMLSConfig.tradeChatMuted(),
                DMLSConfig.serverMessagesMuted(),
                DMLSConfig.staffCommandFeedbackMuted());
    }

    /** Moderation keeps trade chat visible while sharing the two server-message filters. */
    public static boolean shouldHideInModerationView(String cleanMessage) {
        return shouldHide(
                cleanMessage,
                false,
                DMLSConfig.serverMessagesMuted(),
                DMLSConfig.staffCommandFeedbackMuted());
    }

    /** Pure form of the event filter policy, shared by both chat origins and fixture tests. */
    static boolean shouldHide(
            String cleanMessage,
            boolean tradeChatMuted,
            boolean serverMessagesMuted,
            boolean staffCommandFeedbackMuted
    ) {
        return ChatSpamFilterPolicy.shouldHide(
                cleanMessage, tradeChatMuted, serverMessagesMuted, staffCommandFeedbackMuted);
    }
}
