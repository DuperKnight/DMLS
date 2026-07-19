package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.gui.modules.PunishmentHelperScreen;
import com.duperknight.client.moderation.ModerationChatService;
import com.duperknight.client.moderation.ModerationScreen;
import com.duperknight.client.moderation.PunishmentLogService;
import com.duperknight.client.modules.*;
import com.duperknight.client.utils.CannedReplies;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.HubStaffRankDetector;
import com.duperknight.client.utils.GlobalChatMessenger;
import com.duperknight.client.utils.UpdateChecker;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.OperationCancelResult;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.AlertWordlist;
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

public class DMLSClient implements ClientModInitializer {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

    @Override
    public void onInitializeClient() {
        DMLS.LOGGER.info("Initializing DMLS client, you are a lazy staff member!");
        ServerMessageRouter.register();
        OperationCoordinator.global().register();
        registerDmlsCommand();
        registerMenuKeybind();
        GlobalChatMessenger.register();
        ModerationChatService.register();
        PunishmentLogService.register();
        HubStaffRankDetector.register();
        UpdateChecker.register();
        modules().forEach(DMLSModule::register);
    }

    public static List<DMLSModule> modules() {
        return ModulesHolder.ALL;
    }

    private void registerDmlsCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(buildDmlsCommand()));
    }

    LiteralArgumentBuilder<FabricClientCommandSource> buildDmlsCommand() {
        return ClientCommandManager.literal("dmls")
                        .executes(context -> openHomeScreen(context.getSource().getClient()))
                        .then(staffLiteral("rank")
                                .executes(context -> {
                                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                            "dmls.chat.rank.current", DMLSConfig.staffRank().displayName());
                                    return 1;
                                }))
                        .then(staffLiteral("help")
                                .executes(context -> sendHelp(context.getSource().getClient())))
                        .then(staffLiteral("modview")
                                .executes(context -> openModerationScreen(context.getSource().getClient())))
                        .then(staffLiteral("cancel")
                                .executes(context -> cancelActiveOperation(context.getSource().getClient())))
                        .then(staffLiteral("lands")
                                .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                    return module(CheckLandsModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "igns"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(staffLiteral("members")
                                .then(ClientCommandManager.argument("land", StringArgumentType.greedyString()).executes(context -> {
                                    return module(CheckMembersModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "land"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(staffLiteral("alts")
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word()).executes(context -> {
                                    return module(CheckAltsModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "ign"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(staffLiteral("uuid")
                                .then(ClientCommandManager.argument("usernames", StringArgumentType.greedyString()).executes(context -> {
                                    return module(UuidLookupModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "usernames")) ? 1 : 0;
                                })))
                        .then(staffLiteral("xray")
                                .then(ClientCommandManager.literal("confirm")
                                        .executes(context -> module(XrayRollbackModule.class).confirm(
                                                context.getSource().getClient()) ? 1 : 0))
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(XrayRollbackModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word()).executes(context -> {
                                    return module(XrayRollbackModule.class).stage(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "ign")).staged() ? 1 : 0;
                                })))
                        .then(staffLiteral("prefix")
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("limit", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("prefixid", StringArgumentType.word())
                                                        .then(ClientCommandManager.argument("prefixtext", StringArgumentType.greedyString()).executes(context -> {
                                                            return module(PrefixCreateModule.class).submit(context.getSource().getClient(),
                                                                    StringArgumentType.getString(context, "ign"), StringArgumentType.getString(context, "limit"),
                                                                    StringArgumentType.getString(context, "prefixid"),
                                                                    StringArgumentType.getString(context, "prefixtext")).valid() ? 1 : 0;
                                                        }))))))
                        .then(staffLiteral("donorpet")
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("pet", StringArgumentType.word()).executes(context -> {
                                            return module(DonorPetModule.class).submit(context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "ign"),
                                                    StringArgumentType.getString(context, "pet"))
                                                    == DonorPetModule.SubmitStatus.STARTED ? 1 : 0;
                                        }))))
                        .then(staffLiteral("promowave")
                                .then(ClientCommandManager.literal("confirm")
                                        .executes(context -> module(PromoWaveModule.class).confirm(
                                                context.getSource().getClient()) ? 1 : 0))
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(PromoWaveModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("rank", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                            return module(PromoWaveModule.class).stage(context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "rank"),
                                                    StringArgumentType.getString(context, "igns")).staged() ? 1 : 0;
                                        }))))
                        .then(staffLiteral("demowave")
                                .then(ClientCommandManager.literal("confirm")
                                        .executes(context -> module(DemoWaveModule.class).confirm(
                                                context.getSource().getClient()) ? 1 : 0))
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(DemoWaveModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("rank", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                            return module(DemoWaveModule.class).stage(context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "rank"),
                                                    StringArgumentType.getString(context, "igns")).staged() ? 1 : 0;
                                        }))))
                        .then(staffLiteral("activity")
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(ActivityWaveModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                    return module(ActivityWaveModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "igns"))
                                            == ActivityWaveModule.SubmitStatus.STARTED ? 1 : 0;
                                })))
                        .then(staffLiteral("containers")
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(ContainerScanModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("time", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("radius", StringArgumentType.word()).executes(context -> {
                                                    return module(ContainerScanModule.class).submit(context.getSource().getClient(),
                                                            StringArgumentType.getString(context, "ign"),
                                                            StringArgumentType.getString(context, "time"),
                                                            StringArgumentType.getString(context, "radius")).accepted() ? 1 : 0;
                                                })))))
                        .then(staffLiteral("griefs")
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(GriefScanModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("time", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("radius", StringArgumentType.word()).executes(context -> {
                                                    return module(GriefScanModule.class).submit(context.getSource().getClient(),
                                                            StringArgumentType.getString(context, "ign"),
                                                            StringArgumentType.getString(context, "time"),
                                                            StringArgumentType.getString(context, "radius")).accepted() ? 1 : 0;
                                                })))))
                        .then(staffLiteral("co")
                                .executes(context -> {
                                    module(CoreProtectBuilderModule.class).openScreenDeferred(context.getSource().getClient());
                                    return 1;
                                }))
                        .then(staffLiteral("punish")
                                .executes(context -> {
                                    MinecraftClient client = context.getSource().getClient();
                                    client.send(() -> client.setScreen(new PunishmentHelperScreen(null, module(PunishmentHelperModule.class))));
                                    return 1;
                                }))
                        .then(staffLiteral("greet")
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word()).executes(context -> {
                                    CommandDispatch dispatch = module(GreeterModule.class).greet(
                                            context.getSource().getClient(), StringArgumentType.getString(context, "ign").trim());
                                    return dispatch == CommandDispatch.BLOCKED ? 0 : 1;
                                })))
                        .then(staffLiteral("greeter")
                                .executes(context -> {
                                    GreeterModule greeter = module(GreeterModule.class);
                                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                            greeter.enabled() ? "dmls.chat.greeter.enabled" : "dmls.chat.greeter.disabled");
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on").executes(context -> {
                                    return module(GreeterModule.class).setEnabled(context.getSource().getClient(), true) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.literal("off").executes(context -> {
                                    return module(GreeterModule.class).setEnabled(context.getSource().getClient(), false) ? 1 : 0;
                                })))
                        .then(staffLiteral("loc")
                                .executes(context -> {
                                    module(LocationsModule.class).list(context.getSource().getClient()); return 1;
                                })
                                .then(ClientCommandManager.literal("list").executes(context -> {
                                    module(LocationsModule.class).list(context.getSource().getClient()); return 1;
                                }))
                                .then(ClientCommandManager.literal("save")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
                                            return locationOutcomeSucceeded(module(LocationsModule.class).save(
                                                    context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "name"))) ? 1 : 0;
                                        })))
                                .then(ClientCommandManager.literal("tp")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(module(LocationsModule.class).names(), builder))
                                                .executes(context -> {
                                                    return locationOutcomeSucceeded(module(LocationsModule.class).teleport(
                                                            context.getSource().getClient(),
                                                            StringArgumentType.getString(context, "name"))) ? 1 : 0;
                                                })))
                                .then(ClientCommandManager.literal("del")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(module(LocationsModule.class).names(), builder))
                                                .executes(context -> {
                                                    return locationOutcomeSucceeded(module(LocationsModule.class).delete(
                                                            context.getSource().getClient(),
                                                            StringArgumentType.getString(context, "name"))) ? 1 : 0;
                                                }))))
                        .then(staffLiteral("dryrun")
                                .executes(context -> {
                                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                            DMLSConfig.dryRun() ? "dmls.chat.dry_run.status.on" : "dmls.chat.dry_run.status.off");
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> setDryRun(context.getSource().getClient(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> setDryRun(context.getSource().getClient(), false))))
                        .then(staffLiteral("chatlog")
                                .executes(context -> {
                                    module(ChatReplayModule.class).openScreenWithFilter(context.getSource().getClient(), "");
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("filter", StringArgumentType.greedyString()).executes(context -> {
                                    module(ChatReplayModule.class).openScreenWithFilter(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "filter")); return 1;
                                })))
                        .then(staffLiteral("brb")
                                .executes(context -> {
                                    module(AwayModule.class).status(context.getSource().getClient());
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("off").executes(context -> {
                                    module(AwayModule.class).disable(context.getSource().getClient());
                                    return 1;
                                }))
                                .then(ClientCommandManager.argument("duration", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("5m", "15m", "30m", "1h"), builder))
                                        .executes(context -> {
                                            return module(AwayModule.class).startBrb(
                                                    context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "duration")) ? 1 : 0;
                                        })))
                        .then(staffLiteral("dnd")
                                .executes(context -> {
                                    module(AwayModule.class).status(context.getSource().getClient());
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on").executes(context -> {
                                    module(AwayModule.class).setDnd(context.getSource().getClient(), true);
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("off").executes(context -> {
                                    module(AwayModule.class).setDnd(context.getSource().getClient(), false);
                                    return 1;
                                })))
                        .then(buildSayCommand())
                        .then(staffLiteral("alerts")
                                .executes(context -> {
                                    Text state = Text.translatable(DMLSConfig.alertsEnabled() ? "dmls.option.on" : "dmls.option.off")
                                            .formatted(DMLSConfig.alertsEnabled() ? Formatting.GREEN : Formatting.RED);
                                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                            "dmls.chat.alerts.status", state, ChatAlertsModule.wordCount());
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> setAlertsEnabled(context.getSource().getClient(), false)))
                                .then(ClientCommandManager.literal("reload")
                                        .executes(context -> {
                                            AlertWordlist.LoadResult result = ChatAlertsModule.reloadWordlistResult();
                                            int count = result.wordCount();
                                            if (!result.successful()) {
                                                ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                                        "dmls.chat.alerts.reload_failed", count);
                                                return 0;
                                            }
                                            ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                                    count == 1 ? "dmls.chat.alerts.reloaded.one" : "dmls.chat.alerts.reloaded.many", count);
                                            return 1;
                                        })))
                            .then(staffLiteral("eventprotect")
                                    .executes(context -> {
                                        MinecraftClient client = context.getSource().getClient();
                                        client.send(() -> module(EventProtectModule.class).openScreen(client, null));
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("eventName", StringArgumentType.string())
                                            .then(ClientCommandManager.argument("landName", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        MinecraftClient client = context.getSource().getClient();
                                                        EventProtectModule.BroadcastResult result = module(EventProtectModule.class)
                                                                .broadcastProtection(client,
                                                                        StringArgumentType.getString(context, "eventName"),
                                                                        StringArgumentType.getString(context, "landName"));
                                                        return reportProtectResult(client, result) ? 1 : 0;
                                                    }))))
                            .then(staffLiteral("eventrandomtp")
                                    .executes(context -> {
                                        MinecraftClient client = context.getSource().getClient();
                                        String target = module(EventRandomTeleportModule.class).teleportToRandomPlayer(client);
                                        if (target == null) {
                                            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_random_teleport.no_players");
                                            return 0;
                                        }
                            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_random_teleport.teleported", target);
                            return 1;
                        }))
                            .then(staffLiteral("eventpowertool")
                                    .executes(context -> {
                                        MinecraftClient client = context.getSource().getClient();
                                        client.send(() -> module(EventPowerToolModule.class).openScreen(client, null));
                                        return 1;
                                    }));
    }

    private static <T extends DMLSModule> T module(Class<T> type) {
        return modules().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    /** Hides and blocks every DMLS subcommand until the HUB has verified a staff rank. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> staffLiteral(String name) {
        return ClientCommandManager.literal(name)
                .requires(source -> DMLSConfig.hasRecognizedStaffRank());
    }

    private int cancelActiveOperation(MinecraftClient client) {
        OperationCancelResult result = OperationCoordinator.global().cancelActive(client);
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                result == OperationCancelResult.CANCELLED
                        ? "dmls.chat.operation.cancelled"
                        : "dmls.chat.operation.none");
        return result == OperationCancelResult.CANCELLED ? 1 : 0;
    }

    private static boolean locationOutcomeSucceeded(LocationsModule.Outcome outcome) {
        return switch (outcome) {
            case SAVED, UPDATED, DELETED, SENT, SIMULATED -> true;
            default -> false;
        };
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildSayCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> say = staffLiteral("say")
                .executes(context -> {
                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                            "dmls.chat.say.available", String.join(", ", CannedReplies.names()));
                    return 1;
                });

        for (String name : CannedReplies.names()) {
            say.then(ClientCommandManager.literal(name).executes(context -> {
                return CannedReplies.get(name)
                        .map(reply -> ClientUtils.dispatchChatMessage(
                                context.getSource().getClient(), reply).accepted() ? 1 : 0)
                        .orElse(0);
            }));
        }
        return say;
    }

    private void registerMenuKeybind() {
        KeyBinding.Category dmlsCategory = KeyBinding.Category.create(Identifier.of("dmls", "dmls"));
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dmls.open_menu",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                dmlsCategory));
        KeyBinding moderationKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dmls.open_moderation",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                dmlsCategory));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                openHomeScreen(client);
            }
            while (moderationKey.wasPressed()) {
                if (DMLSConfig.hasRecognizedStaffRank()) openModerationScreen(client);
            }
        });
    }

    private int sendHelp(MinecraftClient client) {
        String header = PREFIX;
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
        helpLine(client, "/dmls lands <ign...>", Text.translatable("dmls.help.checklands"));
        helpLine(client, "/dmls members <land>", Text.translatable("dmls.help.checkmembers"));
        helpLine(client, "/dmls alts <ign>", Text.translatable("dmls.help.checkalts", StaffRank.MODERATOR.displayName()));
        helpLine(client, "/dmls uuid <username...>", Text.translatable("dmls.help.uuid"));
        helpLine(client, "/dmls xray <ign>|confirm|cancel", Text.translatable("dmls.help.xray", StaffRank.SENIOR_MODERATOR.displayName()));
        helpLine(client, "/dmls prefix <ign> <limit> <prefixid> <prefixtext>", Text.translatable("dmls.help.prefix", StaffRank.SUPPORT.displayName()));
        helpLine(client, "/dmls donorpet <ign> <pet>", Text.translatable("dmls.help.donorpet", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls promowave <rank> <igns>|confirm|cancel", Text.translatable("dmls.help.promowave", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls demowave <rank> <igns>|confirm|cancel", Text.translatable("dmls.help.demowave", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls activity <igns>|cancel", Text.translatable("dmls.help.activity", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls rank", Text.translatable("dmls.help.rank"));
        helpLine(client, "/dmls alerts [on|off|reload]", Text.translatable("dmls.help.alerts"));
        helpLine(client, "/dmls chatlog [filter]", Text.translatable("dmls.help.chatlog"));
        helpLine(client, "/dmls dryrun <on|off>", Text.translatable("dmls.help.dryrun", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls greet <ign>", Text.translatable("dmls.help.greet"));
        helpLine(client, "/dmls greeter [on|off]", Text.translatable("dmls.help.greeter"));
        helpLine(client, "/dmls loc <save|tp|del|list> [name]", Text.translatable("dmls.help.loc"));
        helpLine(client, "/dmls punish", Text.translatable("dmls.help.punish"));
        helpLine(client, "/dmls co", Text.translatable("dmls.help.co", StaffRank.SENIOR_MODERATOR.displayName()));
        helpLine(client, "/dmls containers <ign|*> <time> <radius>|cancel", Text.translatable("dmls.help.containers", StaffRank.MODERATOR.displayName()));
        helpLine(client, "/dmls griefs <ign|*> <time> <radius>|cancel", Text.translatable("dmls.help.griefs", StaffRank.MODERATOR.displayName()));
        helpLine(client, "/dmls brb <duration|off>", Text.translatable("dmls.help.brb"));
        helpLine(client, "/dmls dnd <on|off>", Text.translatable("dmls.help.dnd"));
        helpLine(client, "/dmls say [reply]", Text.translatable("dmls.help.say"));
        helpLine(client, "/dmls cancel", Text.translatable("dmls.help.cancel"));
        helpLine(client, "/dmls modview", Text.translatable("dmls.help.modview"));
        helpLine(client, "/dmls eventprotect", Text.translatable("dmls.help.eventprotect"));
        helpLine(client, "/dmls eventrandomtp", Text.translatable("dmls.help.eventrandomteleport"));
        helpLine(client, "/dmls eventpowertool", Text.translatable("dmls.help.eventpowertool"));
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
        client.send(() -> client.setScreen(new DMLSHomeScreen(modules())));
        return 1;
    }

    private int openModerationScreen(MinecraftClient client) {
        if (!DMLSConfig.hasRecognizedStaffRank()) return 0;
        client.send(() -> client.setScreen(new ModerationScreen(null)));
        return 1;
    }

    private int setDryRun(MinecraftClient client, boolean enabled) {
        if (!DMLSConfig.staffRank().isAtLeast(StaffRank.ADMIN)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                    StaffRank.ADMIN.displayName(), DMLSConfig.staffRank().displayName());
            return 0;
        }

        DMLSConfig.setDryRun(enabled);
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                enabled ? "dmls.chat.dry_run.enabled" : "dmls.chat.dry_run.disabled");
        return 1;
    }

    private int setAlertsEnabled(MinecraftClient client, boolean enabled) {
        if (!DMLSConfig.setAlertsEnabled(enabled)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.config.save_failed");
            return 0;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                enabled ? "dmls.chat.alerts.enabled" : "dmls.chat.alerts.disabled");
        return 1;
    }

    private boolean reportProtectResult(MinecraftClient client, EventProtectModule.BroadcastResult result) {
        return switch (result) {
            case SENT -> true;
            case SIMULATED -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.dry_run.would_run",
                        "broadcastraw public ...");
                yield true;
            }
            case INVALID_EVENT -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_protect.name");
                yield false;
            }
            case INVALID_LAND -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_protect.land");
                yield false;
            }
            case RANK_BLOCKED -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.department.required",
                        StaffDepartment.EVENTS.displayName());
                yield false;
            }
            case SERVER_BLOCKED -> false; // module already sent the guard message
        };
    }
    /** Defers config-backed module construction until normal client initialization. */
    private static final class ModulesHolder {
        private static final List<DMLSModule> ALL = List.of(
                new ActivityWaveModule(),
                new AwayModule(),
                new ChatAlertsModule(),
                new ChatReplayModule(),
                new ChatSpamMuteModule(),
                new CheckAltsModule(),
                new CheckLandsModule(),
                new CheckMembersModule(),
                new ContainerScanModule(),
                new CoreProtectBuilderModule(),
                new DemoWaveModule(),
                new DonorPetModule(),
                new EventPowerToolModule(),
                new EventProtectModule(),
                new EventRandomTeleportModule(),
                new GreeterModule(),
                new GriefScanModule(),
                new LocationsModule(),
                new MiniMeHudModule(),
                new PrefixCreateModule(),
                new PromoWaveModule(),
                new PunishmentHelperModule(),
                new UuidLookupModule(),
                new XrayRollbackModule()
        );
    }
}
