package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ChatSpamMuteScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/** Client-side filter for repetitive trade chat and server messages. */
public final class ChatSpamMuteModule extends DMLSModule {
    public ChatSpamMuteModule() {
        super(StaffRank.ADMIN);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.chat_spam.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BARRIER);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.chat_spam.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ChatSpamMuteScreen(parent, this));
    }

    @Override
    public void register() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> !shouldHide(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !shouldHide(message));
    }

    private boolean shouldHide(Text message) {
        return shouldHide(
                ChatUtils.cleanLine(message.getString()),
                DMLSConfig.tradeChatMuted(),
                DMLSConfig.serverMessagesMuted());
    }

    /** Pure form of the event filter policy, shared by both chat origins and fixture tests. */
    static boolean shouldHide(
            String cleanMessage,
            boolean tradeChatMuted,
            boolean serverMessagesMuted
    ) {
        return ChatSpamFilterPolicy.shouldHide(cleanMessage, tradeChatMuted, serverMessagesMuted);
    }
}
