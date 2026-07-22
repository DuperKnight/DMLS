package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.accountlink.DiscordLinkAvailability;
import com.duperknight.client.gui.DMLSHomeScreen;
import com.duperknight.client.gui.modules.RulebookScreen;
import com.duperknight.client.moderation.ModerationChatService;
import com.duperknight.client.moderation.ModerationScreen;
import com.duperknight.client.moderation.PunishmentLogService;
import com.duperknight.client.modules.*;
import com.duperknight.client.utils.CannedReplies;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.HubStaffRankDetector;
import com.duperknight.client.utils.UpdateChecker;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.rulebook.RulebookService;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.OperationCancelResult;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.AlertWordlist;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
        DiscordLinkAvailability.warmUp(MinecraftClient.getInstance());
        ServerMessageRouter.register();
        OperationCoordinator.global().register();
        registerDmlsCommand();
        registerScreenKeybinds();
        ModerationChatService.register();
        PunishmentLogService.register();
        RulebookService.shared().register();
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
                        .then(staffLiteral("punish")
                                .executes(context -> {
                                    MinecraftClient client = context.getSource().getClient();
                                    client.send(() -> client.setScreen(new RulebookScreen(null, null)));
                                    return 1;
                                }))
                        .then(staffLiteral("cancel")
                                .executes(context -> cancelActiveOperation(context.getSource().getClient())))
                        .then(moduleLiteral("lands", CheckLandsModule.class)
                                .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                    return module(CheckLandsModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "igns"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(moduleLiteral("members", CheckMembersModule.class)
                                .then(ClientCommandManager.argument("land", StringArgumentType.greedyString()).executes(context -> {
                                    return module(CheckMembersModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "land"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(moduleLiteral("alts", CheckAltsModule.class)
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word()).executes(context -> {
                                    return module(CheckAltsModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "ign"))
                                            == OperationStartResult.STARTED ? 1 : 0;
                                })))
                        .then(moduleLiteral("uuid", UuidLookupModule.class)
                                .then(ClientCommandManager.argument("usernames", StringArgumentType.greedyString()).executes(context -> {
                                    return module(UuidLookupModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "usernames")) ? 1 : 0;
                                })))
                        .then(moduleLiteral("xray", XrayRollbackModule.class)
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
                        .then(moduleLiteral("prefix", PrefixCreateModule.class)
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("limit", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("prefixid", StringArgumentType.word())
                                                        .then(ClientCommandManager.argument("prefixtext", StringArgumentType.greedyString()).executes(context -> {
                                                            return module(PrefixCreateModule.class).submit(context.getSource().getClient(),
                                                                    StringArgumentType.getString(context, "ign"), StringArgumentType.getString(context, "limit"),
                                                                    StringArgumentType.getString(context, "prefixid"),
                                                                    StringArgumentType.getString(context, "prefixtext")).valid() ? 1 : 0;
                                                        }))))))
                        .then(moduleLiteral("donorpet", DonorPetModule.class)
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("pet", StringArgumentType.word()).executes(context -> {
                                            return module(DonorPetModule.class).submit(context.getSource().getClient(),
                                                    StringArgumentType.getString(context, "ign"),
                                                    StringArgumentType.getString(context, "pet"))
                                                    == DonorPetModule.SubmitStatus.STARTED ? 1 : 0;
                                        }))))
                        .then(moduleLiteral("promowave", PromoWaveModule.class)
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
                        .then(moduleLiteral("demowave", DemoWaveModule.class)
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
                        .then(moduleLiteral("activity", ActivityWaveModule.class)
                                .then(ClientCommandManager.literal("cancel").executes(context -> {
                                    return module(ActivityWaveModule.class).cancel(context.getSource().getClient()) ? 1 : 0;
                                }))
                                .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString()).executes(context -> {
                                    return module(ActivityWaveModule.class).submit(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "igns"))
                                            == ActivityWaveModule.SubmitStatus.STARTED ? 1 : 0;
                                })))
                        .then(moduleLiteral("containers", ContainerScanModule.class)
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
                        .then(moduleLiteral("griefs", GriefScanModule.class)
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
                        .then(moduleLiteral("co", CoreProtectBuilderModule.class)
                                .executes(context -> {
                                    module(CoreProtectBuilderModule.class).openScreenDeferred(context.getSource().getClient());
                                    return 1;
                                }))
                        .then(moduleLiteral("greet", GreeterModule.class)
                                .then(ClientCommandManager.argument("ign", StringArgumentType.word()).executes(context -> {
                                    CommandDispatch dispatch = module(GreeterModule.class).greet(
                                            context.getSource().getClient(), StringArgumentType.getString(context, "ign").trim());
                                    return dispatch == CommandDispatch.BLOCKED ? 0 : 1;
                                })))
                        .then(moduleLiteral("greeter", GreeterModule.class)
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
                        .then(moduleLiteral("loc", LocationsModule.class)
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
                        .then(rankLiteral("dryrun", StaffRank.ADMIN)
                                .executes(context -> {
                                    ChatUtils.sendTranslatedMessage(context.getSource().getClient(), PREFIX,
                                            DMLSConfig.dryRun() ? "dmls.chat.dry_run.status.on" : "dmls.chat.dry_run.status.off");
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> setDryRun(context.getSource().getClient(), true)))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> setDryRun(context.getSource().getClient(), false))))
                        .then(moduleLiteral("chatlog", ChatReplayModule.class)
                                .executes(context -> {
                                    module(ChatReplayModule.class).openScreenWithFilter(context.getSource().getClient(), "");
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("filter", StringArgumentType.greedyString()).executes(context -> {
                                    module(ChatReplayModule.class).openScreenWithFilter(context.getSource().getClient(),
                                            StringArgumentType.getString(context, "filter")); return 1;
                                })))
                        .then(moduleLiteral("brb", AwayModule.class)
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
                        .then(moduleLiteral("dnd", AwayModule.class)
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
                        .then(moduleLiteral("alerts", ChatAlertsModule.class)
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
                            .then(moduleLiteral("event", EventProtectModule.class)
                                    .then(moduleLiteral("protect", EventProtectModule.class)
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
                                    .then(moduleLiteral("randomtp", EventRandomTeleportModule.class)
                                            .executes(context -> {
                                                MinecraftClient client = context.getSource().getClient();
                                                EventRandomTeleportModule.TeleportResult result =
                                                        module(EventRandomTeleportModule.class).teleport(client);
                                                return reportRandomTeleportResult(client, result) ? 1 : 0;
                                            }))
                                    .then(moduleLiteral("powertool", EventPowerToolModule.class)
                                            .executes(context -> {
                                                MinecraftClient client = context.getSource().getClient();
                                                client.send(() -> module(EventPowerToolModule.class).openScreen(client, null));
                                                return 1;
                                            }))

                                    .then(moduleLiteral("simultaneous", EventSimultaneousCommandModule.class)
                                                        .executes(context -> {
                                                            MinecraftClient client = context.getSource().getClient();
                                                            client.send(() -> module(EventSimultaneousCommandModule.class).openScreen(client, null));
                                                            return 1;
                                                        })
                                                        .then(ClientCommandManager.literal("command1")
                                                                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                                                        .executes(context -> {
                                                                            MinecraftClient client = context.getSource().getClient();
                                                                            return module(EventSimultaneousCommandModule.class).setCommandOne(client,
                                                                                    StringArgumentType.getString(context, "command")) ? 1 : 0;
                                                                        })))
                                                        .then(ClientCommandManager.literal("command2")
                                                                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                                                        .executes(context -> {
                                                                            MinecraftClient client = context.getSource().getClient();
                                                                            return module(EventSimultaneousCommandModule.class).setCommandTwo(client,
                                                                                    StringArgumentType.getString(context, "command")) ? 1 : 0;
                                                                        })))
                                                        .then(ClientCommandManager.literal("set")
                                                                .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(
                                                                                1, EventSimultaneousCommandModule.MAX_COMMANDS))
                                                                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                                                                .executes(context -> {
                                                                                    MinecraftClient client = context.getSource().getClient();
                                                                                    return module(EventSimultaneousCommandModule.class).setCommand(client,
                                                                                            IntegerArgumentType.getInteger(context, "slot"),
                                                                                            StringArgumentType.getString(context, "command")) ? 1 : 0;
                                                                                }))))
                                                        .then(ClientCommandManager.literal("run")
                                                                .executes(context -> {
                                                                    MinecraftClient client = context.getSource().getClient();
                                                                    EventSimultaneousCommandModule.RunResult result =
                                                                            module(EventSimultaneousCommandModule.class).runStored(client);
                                                                    return reportSimultaneousResult(client, result) ? 1 : 0;
                                                                }))));
    }

    private static <T extends DMLSModule> T module(Class<T> type) {
        return modules().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    /** Hides and blocks every DMLS subcommand until the HUB has verified a staff rank. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> staffLiteral(String name) {
        return ClientCommandManager.literal(name)
                .requires(source -> DMLSConfig.hasRecognizedStaffRank());
    }

    /** Dynamically hides and blocks a command until the detected staff rank meets its requirement. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> rankLiteral(String name, StaffRank minimumRank) {
        return ClientCommandManager.literal(name)
                .requires(source -> DMLSConfig.staffRank().isAtLeast(minimumRank));
    }

    /** Uses the module's live staff and department requirements for autocomplete and parsing. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> moduleLiteral(
            String name,
            Class<? extends DMLSModule> moduleType
    ) {
        return ClientCommandManager.literal(name)
                .requires(source -> module(moduleType).isCommandAvailable(source.getClient()));
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
        LiteralArgumentBuilder<FabricClientCommandSource> say = rankLiteral("say", StaffRank.HELPER)
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

    private void registerScreenKeybinds() {
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
        KeyBinding rulebookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dmls.open_rulebook",
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
            while (rulebookKey.wasPressed()) {
                if (DMLSConfig.hasRecognizedStaffRank() && !(client.currentScreen instanceof RulebookScreen)) {
                    client.setScreen(new RulebookScreen(client.currentScreen, null));
                }
            }
        });
    }

    private int sendHelp(MinecraftClient client) {
        String header = PREFIX;
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
        moduleHelpLine(client, CheckLandsModule.class,
                "/dmls lands <ign...>", Text.translatable("dmls.help.checklands"));
        moduleHelpLine(client, CheckMembersModule.class,
                "/dmls members <land>", Text.translatable("dmls.help.checkmembers"));
        moduleHelpLine(client, CheckAltsModule.class,
                "/dmls alts <ign>", Text.translatable("dmls.help.checkalts", StaffRank.MODERATOR.displayName()));
        moduleHelpLine(client, UuidLookupModule.class,
                "/dmls uuid <username...>", Text.translatable("dmls.help.uuid"));
        moduleHelpLine(client, XrayRollbackModule.class,
                "/dmls xray <ign>|confirm|cancel", Text.translatable("dmls.help.xray", StaffRank.SENIOR_MODERATOR.displayName()));
        moduleHelpLine(client, PrefixCreateModule.class,
                "/dmls prefix <ign> <limit> <prefixid> <prefixtext>", Text.translatable("dmls.help.prefix", StaffRank.SUPPORT.displayName()));
        moduleHelpLine(client, DonorPetModule.class,
                "/dmls donorpet <ign> <pet>", Text.translatable("dmls.help.donorpet", StaffRank.ADMIN.displayName()));
        moduleHelpLine(client, PromoWaveModule.class,
                "/dmls promowave <rank> <igns>|confirm|cancel", Text.translatable("dmls.help.promowave", StaffRank.ADMIN.displayName()));
        moduleHelpLine(client, DemoWaveModule.class,
                "/dmls demowave <rank> <igns>|confirm|cancel", Text.translatable("dmls.help.demowave", StaffRank.ADMIN.displayName()));
        moduleHelpLine(client, ActivityWaveModule.class,
                "/dmls activity <igns>|cancel", Text.translatable("dmls.help.activity", StaffRank.ADMIN.displayName()));
        helpLine(client, "/dmls rank", Text.translatable("dmls.help.rank"));
        moduleHelpLine(client, ChatAlertsModule.class,
                "/dmls alerts [on|off|reload]", Text.translatable("dmls.help.alerts"));
        moduleHelpLine(client, ChatReplayModule.class,
                "/dmls chatlog [filter]", Text.translatable("dmls.help.chatlog"));
        rankHelpLine(client, StaffRank.ADMIN,
                "/dmls dryrun <on|off>", Text.translatable("dmls.help.dryrun", StaffRank.ADMIN.displayName()));
        moduleHelpLine(client, GreeterModule.class,
                "/dmls greet <ign>", Text.translatable("dmls.help.greet"));
        moduleHelpLine(client, GreeterModule.class,
                "/dmls greeter [on|off]", Text.translatable("dmls.help.greeter"));
        moduleHelpLine(client, LocationsModule.class,
                "/dmls loc <save|tp|del|list> [name]", Text.translatable("dmls.help.loc"));
        moduleHelpLine(client, CoreProtectBuilderModule.class,
                "/dmls co", Text.translatable("dmls.help.co", StaffRank.SENIOR_MODERATOR.displayName()));
        moduleHelpLine(client, ContainerScanModule.class,
                "/dmls containers <ign|*> <time> <radius>|cancel", Text.translatable("dmls.help.containers", StaffRank.MODERATOR.displayName()));
        moduleHelpLine(client, GriefScanModule.class,
                "/dmls griefs <ign|*> <time> <radius>|cancel", Text.translatable("dmls.help.griefs", StaffRank.MODERATOR.displayName()));
        moduleHelpLine(client, AwayModule.class,
                "/dmls brb <duration|off>", Text.translatable("dmls.help.brb"));
        moduleHelpLine(client, AwayModule.class,
                "/dmls dnd <on|off>", Text.translatable("dmls.help.dnd"));
        rankHelpLine(client, StaffRank.HELPER,
                "/dmls say [reply]", Text.translatable("dmls.help.say"));
        helpLine(client, "/dmls cancel", Text.translatable("dmls.help.cancel"));
        helpLine(client, "/dmls modview", Text.translatable("dmls.help.modview"));
        helpLine(client, "/dmls punish", Text.translatable("dmls.help.punish"));
        moduleHelpLine(client, EventProtectModule.class,
                "/dmls event protect [\"event name\" <land>]", Text.translatable("dmls.help.eventprotect"));
        moduleHelpLine(client, EventRandomTeleportModule.class,
                "/dmls event randomtp", Text.translatable("dmls.help.eventrandomteleport"));
        moduleHelpLine(client, EventPowerToolModule.class,
                "/dmls event powertool", Text.translatable("dmls.help.eventpowertool"));
        moduleHelpLine(client, EventSimultaneousCommandModule.class,
                "/dmls event simultaneous [set <1-5> <command>|run]", Text.translatable("dmls.help.eventsimultaneous"));

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

    private void rankHelpLine(MinecraftClient client, StaffRank minimumRank, String command, Text description) {
        if (DMLSConfig.staffRank().isAtLeast(minimumRank)) {
            helpLine(client, command, description);
        }
    }

    private void moduleHelpLine(MinecraftClient client, Class<? extends DMLSModule> moduleType,
                                String command, Text description) {
        if (module(moduleType).isAvailableToDetectedRank()) {
            helpLine(client, command, description);
        }
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
            case SENT, SIMULATED -> true;
            case INVALID_EVENT -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_protect.name");
                yield false;
            }
            case INVALID_LAND -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_protect.land");
                yield false;
            }
            case RANK_BLOCKED -> false; // module already sent the rank/department message
            case SERVER_BLOCKED -> false; // module already sent the guard message
        };
    }

    private boolean reportRandomTeleportResult(
            MinecraftClient client,
            EventRandomTeleportModule.TeleportResult result
    ) {
        return switch (result.status()) {
            case SENT -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        "dmls.chat.event_random_teleport.teleported", result.target());
                yield true;
            }
            case SIMULATED -> true; // module already sent one concise dry-run summary
            case NO_PLAYERS -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        "dmls.chat.event_random_teleport.no_players");
                yield false;
            }
            case BLOCKED -> false; // module already sent the rank, guard, or dispatch message
        };
    }
    private boolean reportSimultaneousResult(MinecraftClient client, EventSimultaneousCommandModule.RunResult result) {
        return switch (result) {
            case SENT -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_simultaneous.sent_stored");
                yield true;
            }
            case SIMULATED -> true;
            case INVALID_COMMAND_COUNT -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_simultaneous.stored_count");
                yield false;
            }
            case INVALID_COMMAND -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_simultaneous.stored_gap");
                yield false;
            }
            case RANK_BLOCKED, SERVER_BLOCKED -> false; // module already sent that message
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
                new DoNotInstaBanModule(),
                new EventPowerToolModule(),
                new EventProtectModule(),
                new EventRandomTeleportModule(),
                new EventSimultaneousCommandModule(),
                new GreeterModule(),
                new GriefScanModule(),
                new LocationsModule(),
                new MiniMeHudModule(),
                new PrefixCreateModule(),
                new PromoWaveModule(),
                new UuidLookupModule(),
                new XrayRollbackModule()
        );
    }
}
