package com.duperknight.client.modules;

import com.duperknight.client.utils.AlertWordlist;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.gui.modules.ChatAlertsScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ChatAlertsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Alerts§8] §7";
    private static final AlertWordlist WORDLIST = new AlertWordlist();

    public ChatAlertsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.chat_alerts.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BELL);
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("dmls.module.chat_alerts.description"));
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new ChatAlertsScreen(parent, this));
    }

    @Override
    public void register() {
        WORDLIST.load();
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.PLAYER_CHAT, MessageOrigin.SERVER_SYSTEM), this::check);
    }

    /** Compatibility wrapper used by the command tree. */
    public static int reloadWordlist() {
        return reloadWordlistResult().wordCount();
    }

    public static AlertWordlist.LoadResult reloadWordlistResult() {
        return WORDLIST.load();
    }

    public static AlertWordlist.LoadResult lastWordlistLoadResult() {
        return WORDLIST.lastLoadResult();
    }

    public static int wordCount() {
        return WORDLIST.size();
    }

    /** Pure matcher used by moderation-row highlighting without sounds or chat output. */
    public static Optional<String> findWordlistMatch(String text) {
        return WORDLIST.findMatch(text);
    }

    private void check(ServerMessage message) {
        if (!DMLSConfig.hasRecognizedStaffRank() || !DMLSConfig.alertsEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        String raw = message.cleanText();
        WORDLIST.findMatch(raw).ifPresent(word -> {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.alerts.flagged", word);
            ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.alerts.quote", raw);
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        });
    }
}
