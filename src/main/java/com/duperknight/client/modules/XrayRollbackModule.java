package com.duperknight.client.modules;

import com.duperknight.client.gui.XrayRollbackScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class XrayRollbackModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Xray§8] §7";
    private static final int ROLLBACK_TIMEOUT_TICKS = 20 * 60;
    private static final int BALANCE_WAIT_TICKS = 20 * 2;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final List<Step> STEPS = List.of(
            new Step("Deepslate ores (30d)",
                    "co rollback u:%s t:30d radius:#global a:block include:deepslate, deepslate_gold_ore, deepslate_emerald_ore, deepslate_diamond_ore, deepslate_iron_ore, deepslate_lapis_ore, deepslate_redstone_ore, deepslate_copper_ore, tuff",
                    true),
            new Step("Stone ores (30d)",
                    "co rollback u:%s t:30d radius:#global a:block include:stone, gold_ore, iron_ore, emerald_ore, diamond_ore, redstone_ore, lapis_ore, coal_ore, granite, diorite, andesite, gravel",
                    true),
            new Step("Containers (7d)",
                    "co rollback u:%s t:7d radius:#global action:container",
                    true),
            new Step("Balance check",
                    "bal %s",
                    false)
    );

    private RollbackSession activeSession;

    public XrayRollbackModule() {
        super(StaffRank.SENIOR_MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.literal("Xray Rollback");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.DEEPSLATE_DIAMOND_ORE);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.literal("Roll back a confirmed xrayer: ores and tunnels (30d),"),
                Text.literal("containers (7d), then a balance check.")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new XrayRollbackScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("xray")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), StringArgumentType.getString(context, "ign").trim());
                                    return 1;
                                }))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleServerMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> handleServerMessage(message.getString()));
    }

    /** Starts the xray rollback for the given player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No valid username given.");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendClientMessage(client, PREFIX + "A rollback for §6" + activeSession.ign
                    + "§7 is still running, wait for it to finish.");
            return;
        }

        activeSession = new RollbackSession(ign);
        activeSession.start(client);
    }

    private void handleServerMessage(String message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(ChatUtils.cleanLine(message));
        }
    }

    private record Step(String label, String commandTemplate, boolean waitForCompletion) {
    }

    private final class RollbackSession {
        private final String ign;
        private final List<String> results = new ArrayList<>();

        private int stepIndex = -1;
        private int waitTicks;
        private boolean completionSeen;

        private RollbackSession(String ign) {
            this.ign = ign;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Starting xray rollback for §6" + ign + "§7 (§6" + STEPS.size() + "§7 steps)...");
            nextStep(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            if (stepIndex >= STEPS.size()) {
                return;
            }

            waitTicks++;
            Step step = STEPS.get(stepIndex);
            if (step.waitForCompletion()) {
                if (completionSeen) {
                    results.add("§a✔ §7" + step.label());
                    nextStep(client);
                } else if (waitTicks > ROLLBACK_TIMEOUT_TICKS) {
                    results.add("§e⚠ §7" + step.label() + " §8(no completion message, check manually)");
                    nextStep(client);
                }
            } else if (waitTicks > BALANCE_WAIT_TICKS) {
                results.add("§a✔ §7" + step.label() + " §8(see output above)");
                nextStep(client);
            }
        }

        private void handleServerMessage(String message) {
            if (stepIndex < 0 || stepIndex >= STEPS.size() || !STEPS.get(stepIndex).waitForCompletion()) {
                return;
            }

            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("rollback complete")) {
                completionSeen = true;
            }
        }

        private void nextStep(MinecraftClient client) {
            stepIndex++;
            if (stepIndex >= STEPS.size()) {
                report(client);
                finish();
                return;
            }

            Step step = STEPS.get(stepIndex);
            waitTicks = 0;
            completionSeen = false;
            ChatUtils.sendClientMessage(client, PREFIX + "Step §6" + (stepIndex + 1) + "/" + STEPS.size() + "§7: " + step.label());
            ClientUtils.sendCommand(client, step.commandTemplate().formatted(ign));
        }

        private void report(MinecraftClient client) {
            String header = PREFIX + "Xray rollback for §6" + ign + "§7 done ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (String result : results) {
                ChatUtils.sendClientMessage(client, "§8• " + result);
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private void finish() {
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
