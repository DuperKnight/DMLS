package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventPowerToolScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.EventPowerToolStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EventPowerToolModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - PowerTools§8] §7";

    public EventPowerToolModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_powertool.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.REDSTONE_TORCH);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_powertool.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventPowerToolScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public Map<String, String> saved() {
        return EventPowerToolStorage.all();
    }

    /** Saves a named command, without binding it to anything yet. */
    public boolean save(MinecraftClient client, String name, String command) {
        if (!EventPowerToolStorage.save(name, command)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.save_failed");
            return false;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.saved", name);
        return true;
    }

    /** Binds a saved command to the currently held item via /powertool. */
    public boolean load(MinecraftClient client, String name) {
        Optional<String> command = EventPowerToolStorage.get(name);
        if (command.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.unknown", name);
            return false;
        }
        if (ClientUtils.isNotConnected(client)) {
            return false;
        }
        client.player.networkHandler.sendChatCommand("powertool " + command.get());
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.loaded", name, command.get());
        return true;
    }

    public boolean delete(MinecraftClient client, String name) {
        if (!EventPowerToolStorage.delete(name)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.unknown", name);
            return false;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.deleted", name);
        return true;
    }
    /** Clears the /powertool binding on the currently held item. */
    public boolean clear(MinecraftClient client) {
        if (ClientUtils.isNotConnected(client)) {
            return false;
        }
        client.player.networkHandler.sendChatCommand("powertool");
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.cleared");
        return true;
    }
}