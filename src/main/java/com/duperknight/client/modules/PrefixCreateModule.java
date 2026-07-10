package com.duperknight.client.modules;

import com.duperknight.client.gui.PrefixCreateScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Pattern;

public final class PrefixCreateModule extends DMLSModule {
    public static final List<String> LIMITS = List.of("10", "30");

    private static final String PREFIX = "§8[§6DMLS - Prefix§8] §7";
    private static final int COMMAND_DELAY_TICKS = 20 * 2;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private CreateSession activeSession;

    public PrefixCreateModule() {
        super(StaffRank.SUPPORT);
    }

    @Override
    public Text displayName() {
        return Text.literal("Prefix Creation");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.OAK_SIGN);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.literal("Create a prefix and set its color, player limit"),
                Text.literal("and manager in one go.")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PrefixCreateScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("prefixlazy")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .then(ClientCommandManager.argument("limit", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(LIMITS, builder))
                                        .then(ClientCommandManager.argument("prefixid", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("hexcode", StringArgumentType.greedyString())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("#"), builder))
                                                        .executes(context -> {
                                                            submit(context.getSource().getClient(),
                                                                    StringArgumentType.getString(context, "ign").trim(),
                                                                    StringArgumentType.getString(context, "limit").trim(),
                                                                    StringArgumentType.getString(context, "prefixid").trim(),
                                                                    StringArgumentType.getString(context, "hexcode").trim());
                                                            return 1;
                                                        })))))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
    }

    /** Starts the prefix creation. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign, String limit, String prefixId, String hexCode) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No valid username given.");
            return;
        }

        if (!LIMITS.contains(limit)) {
            ChatUtils.sendClientMessage(client, PREFIX + "The player limit must be §610§7 or §630§7.");
            return;
        }

        if (prefixId.isEmpty()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No prefix id given.");
            return;
        }

        if (hexCode.isEmpty()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No hex code given.");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendClientMessage(client, PREFIX + "A prefix creation is still running, wait for it to finish.");
            return;
        }

        activeSession = new CreateSession(ign, limit, prefixId, hexCode);
        activeSession.start(client);
    }

    private final class CreateSession {
        private final String ign;
        private final String limit;
        private final String prefixId;
        private final String hexCode;
        private final List<String> commands;

        private int commandIndex;
        private int waitTicks;

        private CreateSession(String ign, String limit, String prefixId, String hexCode) {
            this.ign = ign;
            this.limit = limit;
            this.prefixId = prefixId;
            this.hexCode = hexCode;
            this.commands = List.of(
                    "prefix create %s %s".formatted(prefixId, hexCode),
                    "prefix x setlimit %s %s".formatted(prefixId, limit),
                    "prefix x setmanager %s %s".formatted(prefixId, ign),
                    "prefix x info %s".formatted(prefixId)
            );
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Creating prefix §6" + prefixId + "§7 for §6" + ign + "§7...");
            ClientUtils.sendCommand(client, commands.get(0));
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            if (waitTicks < COMMAND_DELAY_TICKS) {
                return;
            }

            waitTicks = 0;
            commandIndex++;
            if (commandIndex >= commands.size()) {
                report(client);
                activeSession = null;
                return;
            }

            ClientUtils.sendCommand(client, commands.get(commandIndex));
        }

        private void report(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Created prefix §6" + prefixId + "§7 with color §6" + hexCode
                    + "§7, player limit §6" + limit + "§7 and manager §6" + ign + "§7. Check the info above to confirm.");
        }
    }
}
