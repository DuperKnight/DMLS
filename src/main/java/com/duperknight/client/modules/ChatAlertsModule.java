package com.duperknight.client.modules;

import com.duperknight.client.utils.AlertWordlist;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.gui.modules.ChatAlertsScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.EnumSet;

public final class ChatAlertsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Alerts§8] §7";
    private static final AlertWordlist WORDLIST = new AlertWordlist();

    public ChatAlertsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.chat_alerts.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BELL);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.chat_alerts.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ChatAlertsScreen(parent, this));
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

    private void check(ServerMessage message) {
        if (!DMLSConfig.hasRecognizedStaffRank() || !DMLSConfig.alertsEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        String raw = message.cleanText();
        WORDLIST.findMatch(raw).ifPresent(word -> {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.alerts.flagged", word);
            ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.alerts.quote", raw);
            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        });
    }
}
