package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class DMLSClient implements ClientModInitializer {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

    private static final List<DMLSModule> MODULES = List.of(
            new CheckLandsModule(),
            new ChatAlertsModule()
    );

    @Override
    public void onInitializeClient() {
        DMLS.LOGGER.info("Initializing DMLS client, you are a lazy staff member!");
        registerDmlsCommand();
        registerMenuKeybind();
        MODULES.forEach(DMLSModule::register);
    }

    public static List<DMLSModule> modules() {
        return MODULES;
    }

    private void registerDmlsCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("dmls")
                        .executes(context -> openHomeScreen(context.getSource().getClient()))
                        .then(ClientCommandManager.literal("rank")
                                .executes(context -> {
                                    ChatUtils.sendClientMessage(context.getSource().getClient(),
                                            PREFIX + "Your rank is set to " + DMLSConfig.staffRank().displayName()
                                                    + "§r§7. Change it with §6/dmls rank <rank>§7.");
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("rank", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(DMLSConfig.RANK_SUGGESTIONS, builder))
                                        .executes(context -> {
                                            MinecraftClient client = context.getSource().getClient();
                                            String input = StringArgumentType.getString(context, "rank");
                                            Optional<StaffRank> rank = DMLSConfig.parseRank(input);
                                            if (rank.isEmpty()) {
                                                ChatUtils.sendClientMessage(client, PREFIX + "Unknown rank §6" + input
                                                        + "§7. Options: §6" + String.join("§7, §6", DMLSConfig.RANK_SUGGESTIONS) + "§7.");
                                                return 0;
                                            }

                                            DMLSConfig.setStaffRank(rank.get());
                                            ChatUtils.sendClientMessage(client, PREFIX + "Rank set to " + rank.get().displayName() + "§r§7.");
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("alerts")
                                .executes(context -> {
                                    ChatUtils.sendClientMessage(context.getSource().getClient(),
                                            PREFIX + "Chat alerts are " + (DMLSConfig.alertsEnabled() ? "§aon" : "§coff")
                                                    + "§7 with §6" + ChatAlertsModule.wordCount() + "§7 words loaded."
                                                    + " Use §6/dmls alerts <on|off|reload>§7.");
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), false)))
                                .then(ClientCommandManager.literal("reload")
                                        .executes(context -> {
                                            int count = ChatAlertsModule.reloadWordlist();
                                            ChatUtils.sendClientMessage(context.getSource().getClient(),
                                                    PREFIX + "Reloaded §6" + count + "§7 alert word" + (count == 1 ? "" : "s") + ".");
                                            return 1;
                                        }))))
        );
    }

    private void registerMenuKeybind() {
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dmls.open_menu",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.Category.create(Identifier.of("dmls", "dmls"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                openHomeScreen(client);
            }
        });
    }

    private int openHomeScreen(MinecraftClient client) {
        // next tick, otherwise the closing chat screen overrides it
        client.send(() -> client.setScreen(new DMLSHomeScreen(MODULES)));
        return 1;
    }

    private int setAlertsEnabled(MinecraftClient client, boolean enabled) {
        DMLSConfig.setAlertsEnabled(enabled);
        ChatUtils.sendClientMessage(client, PREFIX + "Chat alerts " + (enabled ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }
}
