package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.modules.CheckAltsModule;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.modules.CheckMembersModule;
import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.modules.XrayRollbackModule;
import com.duperknight.client.utils.CannedReplies;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class DMLSClient implements ClientModInitializer {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

    private static final List<DMLSModule> MODULES = List.of(
            new CheckLandsModule(),
            new CheckMembersModule(),
            new CheckAltsModule(),
            new XrayRollbackModule(),
            new PrefixCreateModule(),
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
                        .then(ClientCommandManager.literal("help")
                                .executes(context -> sendHelp(context.getSource().getClient())))
                        .then(buildSayCommand())
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

    private LiteralArgumentBuilder<FabricClientCommandSource> buildSayCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> say = ClientCommandManager.literal("say")
                .executes(context -> {
                    ChatUtils.sendClientMessage(context.getSource().getClient(),
                            PREFIX + "Available replies: §6" + String.join("§7, §6", CannedReplies.names()) + "§7.");
                    return 1;
                });

        for (String name : CannedReplies.names()) {
            say.then(ClientCommandManager.literal(name).executes(context -> {
                CannedReplies.get(name).ifPresent(reply -> ClientUtils.sendChatMessage(context.getSource().getClient(), reply));
                return 1;
            }));
        }
        return say;
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

    private int sendHelp(MinecraftClient client) {
        String header = PREFIX;
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
        helpLine(client, "/checklands <ign...>", "Checks which lands the given players are in and their rank in each. Multiple names are checked one after another.");
        helpLine(client, "/checkmembers <land>", "Lists all members of a land grouped by rank. Click a name to run /checklands on them.");
        helpLine(client, "/checkalts <ign>", "Runs /alts and then /history on every found account, with a punishment summary. Requires Moderator.");
        helpLine(client, "/xray <ign>", "Rolls back a confirmed xrayer's ores (30d) and containers (7d), then checks their balance. Requires Sr Mod.");
        helpLine(client, "/prefixlazy <ign> <10|30> <prefixid> <hexcode>", "Creates a prefix and sets its color, player limit and manager in one go.");
        helpLine(client, "/dmls rank [rank]", "Shows or sets your staff rank, which decides what DMLS features you can use.");
        helpLine(client, "/dmls alerts [on|off|reload]", "Shows or toggles chat alerts. Words are configured in config/dmls-alerts.txt.");
        helpLine(client, "/dmls say [reply]", "Sends a pre-written staff reply in chat.");
        helpLine(client, "/dmls", "Opens the DMLS menu.");
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        return 1;
    }

    private void helpLine(MinecraftClient client, String command, String description) {
        String suggested = command.replaceAll(" [<\\[].*", "") + " ";
        ChatUtils.sendClientMessage(client, Text.literal("§8• §6" + command).styled(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(suggested))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7" + description + "\n§8Click to put the command in your chat bar.")))));
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
