package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.modules.CheckAltsModule;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.modules.CheckMembersModule;
import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.modules.DonorPetModule;
import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.modules.PromoWaveModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.modules.TradeChatMuteModule;
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
import net.minecraft.util.Formatting;

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
            new DonorPetModule(),
            new PromoWaveModule(),
            new ChatAlertsModule(),
            new TradeChatMuteModule()
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
                                    ChatUtils.sendClientMessage(context.getSource().getClient(), Text.literal(PREFIX)
                                            .append(Text.translatable("dmls.chat.rank.current", DMLSConfig.staffRank().displayName())));
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("rank", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(DMLSConfig.RANK_SUGGESTIONS, builder))
                                        .executes(context -> {
                                            MinecraftClient client = context.getSource().getClient();
                                            String input = StringArgumentType.getString(context, "rank");
                                            Optional<StaffRank> rank = DMLSConfig.parseRank(input);
                                            if (rank.isEmpty()) {
                                                ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(Text.translatable(
                                                        "dmls.chat.rank.unknown", input, String.join(", ", DMLSConfig.RANK_SUGGESTIONS))));
                                                return 0;
                                            }

                                            DMLSConfig.setStaffRank(rank.get());
                                            ChatUtils.sendClientMessage(client, Text.literal(PREFIX)
                                                    .append(Text.translatable("dmls.chat.rank.set", rank.get().displayName())));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("help")
                                .executes(context -> sendHelp(context.getSource().getClient())))
                        .then(buildSayCommand())
                        .then(ClientCommandManager.literal("alerts")
                                .executes(context -> {
                                    Text state = Text.translatable(DMLSConfig.alertsEnabled() ? "dmls.option.on" : "dmls.option.off")
                                            .formatted(DMLSConfig.alertsEnabled() ? Formatting.GREEN : Formatting.RED);
                                    ChatUtils.sendClientMessage(context.getSource().getClient(), Text.literal(PREFIX)
                                            .append(Text.translatable("dmls.chat.alerts.status", state, ChatAlertsModule.wordCount())));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), false)))
                                .then(ClientCommandManager.literal("reload")
                                        .executes(context -> {
                                            int count = ChatAlertsModule.reloadWordlist();
                                            ChatUtils.sendClientMessage(context.getSource().getClient(), Text.literal(PREFIX).append(Text.translatable(
                                                    count == 1 ? "dmls.chat.alerts.reloaded.one" : "dmls.chat.alerts.reloaded.many", count)));
                                            return 1;
                                        }))))
        );
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildSayCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> say = ClientCommandManager.literal("say")
                .executes(context -> {
                    ChatUtils.sendClientMessage(context.getSource().getClient(), Text.literal(PREFIX)
                            .append(Text.translatable("dmls.chat.say.available", String.join(", ", CannedReplies.names()))));
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
        helpLine(client, "/checklands <ign...>", Text.translatable("dmls.help.checklands"));
        helpLine(client, "/checkmembers <land>", Text.translatable("dmls.help.checkmembers"));
        helpLine(client, "/dmlsalts <ign>", Text.translatable("dmls.help.checkalts", StaffRank.MODERATOR.displayName()));
        helpLine(client, "/xray <ign>", Text.translatable("dmls.help.xray", StaffRank.SENIOR_MODERATOR.displayName()));
        helpLine(client, "/prefixlazy <ign> <limit> <prefixid> <prefixtext>", Text.translatable("dmls.help.prefix", StaffRank.SUPPORT.displayName()));
        helpLine(client, "/donorpet <ign> <pet>", Text.translatable("dmls.help.donorpet", StaffRank.ADMIN.displayName()));
        helpLine(client, "/promowave <rank> <ign1, ign2, ...>", Text.translatable("dmls.help.promowave", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls rank [rank]", Text.translatable("dmls.help.rank"));
        helpLine(client, "/dmls alerts [on|off|reload]", Text.translatable("dmls.help.alerts"));
        helpLine(client, "/dmls say [reply]", Text.translatable("dmls.help.say"));
        helpLine(client, "/dmls", Text.translatable("dmls.help.menu"));
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        return 1;
    }

    private void helpLine(MinecraftClient client, String command, Text description) {
        String suggested = command.replaceAll(" [<\\[].*", "") + " ";
        ChatUtils.sendClientMessage(client, Text.literal("§8• §6" + command).styled(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(suggested))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.help.hover", description)))));
    }

    private int openHomeScreen(MinecraftClient client) {
        // next tick, otherwise the closing chat screen overrides it
        client.send(() -> client.setScreen(new DMLSHomeScreen(MODULES)));
        return 1;
    }

    private int setAlertsEnabled(MinecraftClient client, boolean enabled) {
        DMLSConfig.setAlertsEnabled(enabled);
        ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(Text.translatable(
                enabled ? "dmls.chat.alerts.enabled" : "dmls.chat.alerts.disabled")));
        return 1;
    }
}
