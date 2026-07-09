package com.duperknight.client.modules;

import com.duperknight.client.utils.AlertWordlist;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.gui.ChatAlertsScreen;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;

public final class ChatAlertsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Alerts§8] §7";
    private static final AlertWordlist WORDLIST = new AlertWordlist();

    public ChatAlertsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.literal("Chat Alerts");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BELL);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.literal("Receive an alert when a configured word appears in chat."));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ChatAlertsScreen(parent, this));
    }

    @Override
    public void register() {
        WORDLIST.load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                check(message);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> check(message));
    }

    public static int reloadWordlist() {
        return WORDLIST.load();
    }

    public static int wordCount() {
        return WORDLIST.size();
    }

    private void check(Text message) {
        if (!DMLSConfig.alertsEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        String raw = ChatUtils.cleanLine(message.getString());
        WORDLIST.findMatch(raw).ifPresent(word -> {
            ChatUtils.sendClientMessage(client, PREFIX + "Flagged word §c" + word + "§7 detected:");
            ChatUtils.sendClientMessage(client, "§8> §f" + raw);
            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        });
    }
}
