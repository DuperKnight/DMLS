package com.duperknight.client.modules;

import com.duperknight.client.gui.PromoWaveScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;

public final class PromoWaveModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - PromoWave§8] §7";
    // LuckPerms silently ignores commands that arrive too quickly ("is spamming LuckPerms commands"),
    // so leave a generous gap between them.
    private static final int COMMAND_DELAY_TICKS = 20 * 3;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Map<String, List<String>> RANK_COMMANDS = new LinkedHashMap<>();

    static {
        // The add always runs before the remove, so a failed command can never leave someone rankless.
        RANK_COMMANDS.put("helper", List.of(
                "lp user %s parent add helper"));
        RANK_COMMANDS.put("mod", List.of(
                "lp user %s parent add mod",
                "lp user %s parent remove helper"));
        RANK_COMMANDS.put("sr-mod", List.of(
                "lp user %s parent add sr-mod",
                "lp user %s parent remove mod"));
        RANK_COMMANDS.put("support", List.of(
                "lp user %s parent add support"));
        RANK_COMMANDS.put("admin", List.of(
                "lp user %s parent add admin",
                "lp user %s parent remove sr-mod"));
    }

    private PromoSession activeSession;

    public PromoWaveModule() {
        super(StaffRank.ADMIN);
    }

    /** Returns the names of all promotable ranks. */
    public static List<String> ranks() {
        return List.copyOf(RANK_COMMANDS.keySet());
    }

    @Override
    public Text displayName() {
        return Text.literal("Promo Wave");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLDEN_HELMET);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.literal("Promote a whole wave of staff to a rank in one command."),
                Text.literal("Separate names with commas or spaces.")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PromoWaveScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var promowave = ClientCommandManager.literal("promowave");
            for (String rank : RANK_COMMANDS.keySet()) {
                promowave.then(ClientCommandManager.literal(rank)
                        .then(ClientCommandManager.argument("igns", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), rank, StringArgumentType.getString(context, "igns"));
                                    return 1;
                                })));
            }
            dispatcher.register(promowave);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
    }

    /** Starts a promotion wave. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String rank, String input) {
        if (!hasRequiredRank(client)) {
            return;
        }

        List<String> commandsPerPlayer = RANK_COMMANDS.get(rank);
        if (commandsPerPlayer == null) {
            ChatUtils.sendClientMessage(client, PREFIX + "Unknown rank §6" + rank + "§7. Options: §6"
                    + String.join("§7, §6", ranks()) + "§7.");
            return;
        }

        List<String> igns = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String ign : input.trim().split("[,\\s]+")) {
            if (ign.isEmpty()) {
                continue;
            }
            if (!USERNAME.matcher(ign).matches()) {
                skipped.add(ign);
            } else if (igns.stream().noneMatch(ign::equalsIgnoreCase)) {
                igns.add(ign);
            }
        }

        if (!skipped.isEmpty()) {
            ChatUtils.sendClientMessage(client, PREFIX + "Skipping invalid name" + (skipped.size() == 1 ? "" : "s")
                    + ": §6" + String.join("§7, §6", skipped) + "§7.");
        }

        if (igns.isEmpty()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No valid usernames given.");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendClientMessage(client, PREFIX + "A promotion wave is still running, wait for it to finish.");
            return;
        }

        activeSession = new PromoSession(rank, commandsPerPlayer, igns);
        activeSession.start(client);
    }

    private final class PromoSession {
        private final String rank;
        private final List<String> commandsPerPlayer;
        private final List<String> igns;
        private final Queue<String> remainingCommands = new ArrayDeque<>();

        private int playerIndex;
        private int commandIndexWithinPlayer;
        private int waitTicks;

        private PromoSession(String rank, List<String> commandsPerPlayer, List<String> igns) {
            this.rank = rank;
            this.commandsPerPlayer = commandsPerPlayer;
            this.igns = igns;
            for (String ign : igns) {
                for (String template : commandsPerPlayer) {
                    remainingCommands.add(template.formatted(ign));
                }
            }
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Promoting §6" + igns.size() + "§7 player"
                    + (igns.size() == 1 ? "" : "s") + " to §6" + rank + "§7...");
            sendNext(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            if (waitTicks >= COMMAND_DELAY_TICKS) {
                sendNext(client);
            }
        }

        private void sendNext(MinecraftClient client) {
            waitTicks = 0;
            String command = remainingCommands.poll();
            if (command == null) {
                report(client);
                activeSession = null;
                return;
            }

            if (commandIndexWithinPlayer == 0) {
                ChatUtils.sendClientMessage(client, PREFIX + "Promoting §6" + igns.get(playerIndex)
                        + "§7 §8(" + (playerIndex + 1) + "/" + igns.size() + ")");
            }

            ClientUtils.sendCommand(client, command);
            commandIndexWithinPlayer++;
            if (commandIndexWithinPlayer >= commandsPerPlayer.size()) {
                commandIndexWithinPlayer = 0;
                playerIndex++;
            }
        }

        private void report(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Done! Promoted §6" + igns.size() + "§7 player"
                    + (igns.size() == 1 ? "" : "s") + " to §6" + rank + "§7: §6" + String.join("§7, §6", igns)
                    + "§7. Check the LuckPerms output above for errors.");
        }
    }
}
