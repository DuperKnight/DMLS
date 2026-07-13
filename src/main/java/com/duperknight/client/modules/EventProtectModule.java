package com.duperknight.client.modules;

import com.duperknight.client.gui.EventProtectScreen;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;


import java.util.List;

public final class EventProtectModule extends DMLSModule{

    private String eventName;

    public EventProtectModule(){
        super(StaffRank.SENIOR_MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_protect.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SHIELD);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_protect.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventProtectScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     Issues /broadcastraw with the protection notice for the currently set event.
     */
    public void broadcastProtection() {
        if (!DMLSConfig.alertsEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        if (eventName == null || eventName.isBlank()) {
            return;
        }

        String message = "&7&l" + eventName + " is starting, and is protected by staff: "
                + "bandits and raiders are "+"&4&l"+ "not"+"&7&l"+" allowed to interfere.";

        client.player.networkHandler.sendChatCommand("broadcastraw public " + message);
    }

}