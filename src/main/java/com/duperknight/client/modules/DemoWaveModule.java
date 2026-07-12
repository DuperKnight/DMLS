package com.duperknight.client.modules;

import com.duperknight.client.gui.DemoWaveScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ServerGuard;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.parser.LuckPermsResponseParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;

/** The inverse of the promo wave: removes a staff rank from a whole wave for seasonal cleanup. */
public final class DemoWaveModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - DemoWave§8] §7";
    // LuckPerms silently ignores commands that arrive too quickly ("is spamming LuckPerms commands"),
    // so leave a generous gap between them.
    private static final int COMMAND_DELAY_TICKS = 20 * 3;
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;
    private static final List<String> RANKS = List.of("helper", "mod", "sr-mod", "support", "admin");

    private DemoSession activeSession;

    public DemoWaveModule() {
        super(StaffRank.ADMIN);
    }

    /** Returns the names of all removable ranks. */
    public static List<String> ranks() {
        return RANKS;
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.demo_wave.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.LEATHER_HELMET);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.demo_wave.description.1"),
                Text.translatable("dmls.module.demo_wave.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new DemoWaveScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var demowave = ClientCommandManager.literal("demowave");
            for (String rank : RANKS) {
                demowave.then(ClientCommandManager.literal(rank)
                        .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), rank, StringArgumentType.getString(context, "igns"));
                                    return 1;
                                })));
            }
            dispatcher.register(demowave);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    private void handleServerMessage(ServerMessage message) {
        if (activeSession != null) activeSession.handleServerMessage(message.cleanText());
    }

    /** Starts a demotion wave. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String rank, String input) {
        if (!canRunPrivilegedOperation(client)) {
            return;
        }

        if (!RANKS.contains(rank)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.unknown_rank", rank, String.join(", ", RANKS));
            return;
        }

        List<String> igns = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        igns.addAll(InputValidators.uniqueUsernames(input, skipped));

        if (!skipped.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    skipped.size() == 1 ? "dmls.chat.demo.skipping.one" : "dmls.chat.demo.skipping.many",
                    String.join(", ", skipped));
        }

        if (igns.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.active");
            return;
        }

        activeSession = new DemoSession(rank, igns);
        activeSession.start(client);
    }

    private final class DemoSession {
        private final String rank;
        private final List<String> igns;
        private final Queue<String> remainingIgns = new ArrayDeque<>();
        private final String serverIdentity;
        private final List<String> dispatchedPlayers = new ArrayList<>();

        private int playerIndex;
        private int waitTicks;
        private boolean awaitingResponse;
        private String awaitingIgn;

        private DemoSession(String rank, List<String> igns) {
            this.rank = rank;
            this.igns = igns;
            this.serverIdentity = ServerGuard.connectionIdentity(MinecraftClient.getInstance());
            remainingIgns.addAll(igns);
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    igns.size() == 1 ? "dmls.chat.demo.start.one" : "dmls.chat.demo.start.many", igns.size(), rank);
            sendNext(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client) || !serverIdentity.equals(ServerGuard.connectionIdentity(client))) {
                cancel(client);
                return;
            }

            waitTicks++;
            if (awaitingResponse && waitTicks >= RESPONSE_TIMEOUT_TICKS) {
                cancel(client);
            } else if (!awaitingResponse && waitTicks >= COMMAND_DELAY_TICKS) {
                sendNext(client);
            }
        }

        private void sendNext(MinecraftClient client) {
            waitTicks = 0;
            String ign = remainingIgns.poll();
            if (ign == null) {
                report(client);
                activeSession = null;
                return;
            }

            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.progress", ign, playerIndex + 1, igns.size());
            awaitingIgn = ign;
            if (!ClientUtils.sendCommand(client, "lp user %s parent remove %s".formatted(ign, rank))) {
                cancel(client);
                return;
            }
            awaitingResponse = true;
        }

        private void handleServerMessage(String message) {
            if (!awaitingResponse || LuckPermsResponseParser.parseParentChange(
                    LuckPermsResponseParser.Action.REMOVE, awaitingIgn, rank, message) != LuckPermsResponseParser.Result.CONFIRMED) {
                return;
            }
            awaitingResponse = false;
            waitTicks = 0;
            dispatchedPlayers.add(awaitingIgn);
            playerIndex++;
        }

        private void report(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    igns.size() == 1 ? "dmls.chat.demo.confirmed.one" : "dmls.chat.demo.confirmed.many",
                    dispatchedPlayers.size(), rank, String.join(", ", dispatchedPlayers));
        }

        private void cancel(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.demo.cancelled",
                    dispatchedPlayers.size(), igns.size());
            if (activeSession == this) activeSession = null;
        }
    }
}
