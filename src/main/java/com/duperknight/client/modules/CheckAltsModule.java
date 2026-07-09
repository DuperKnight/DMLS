package com.duperknight.client.modules;

import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CheckAltsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - CheckAlts§8] §7";
    private static final int ALTS_TIMEOUT_TICKS = 20 * 10;
    private static final int HISTORY_WINDOW_TICKS = 20 * 3;
    private static final int MAX_ACCOUNTS = 10;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private CheckSession activeSession;

    public CheckAltsModule() {
        super(StaffRank.MODERATOR);
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("checkalts")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(context -> {
                                    MinecraftClient client = context.getSource().getClient();
                                    if (!hasRequiredRank(client)) {
                                        return 0;
                                    }
                                    start(client, StringArgumentType.getString(context, "ign"));
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

    private void start(MinecraftClient client, String ign) {
        if (activeSession != null) {
            activeSession.cancel(client, "Started a new check for §6" + ign + "§7.");
        }

        activeSession = new CheckSession(ign);
        activeSession.start(client);
    }

    private void handleServerMessage(String message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(ChatUtils.cleanLine(message));
        }
    }

    private enum Stage {
        WAITING_FOR_ALTS,
        WAITING_FOR_HISTORY
    }

    private static final class PunishmentStats {
        private final String name;
        private int bans;
        private int mutes;
        private int warns;
        private int kicks;

        private PunishmentStats(String name) {
            this.name = name;
        }

        private boolean isClean() {
            return bans == 0 && mutes == 0 && warns == 0 && kicks == 0;
        }
    }

    private final class CheckSession {
        private final String ign;
        private final Queue<String> remainingAccounts = new ArrayDeque<>();
        private final List<PunishmentStats> results = new ArrayList<>();

        private Stage stage = Stage.WAITING_FOR_ALTS;
        private PunishmentStats currentStats;
        private int waitTicks;
        private boolean altsParsed;

        private CheckSession(String ign) {
            this.ign = ign;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Checking alts of §6" + ign + "§7...");
            ClientUtils.sendCommand(client, "alts " + ign);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            switch (stage) {
                case WAITING_FOR_ALTS -> {
                    if (waitTicks > ALTS_TIMEOUT_TICKS) {
                        ChatUtils.sendClientMessage(client, PREFIX + "Could not read the §6/alts§7 output, checking history of §6"
                                + ign + "§7 only.");
                        beginHistoryChecks(client, List.of());
                    }
                }
                case WAITING_FOR_HISTORY -> {
                    if (waitTicks > HISTORY_WINDOW_TICKS) {
                        results.add(currentStats);
                        sendNextHistoryCommand(client);
                    }
                }
            }
        }

        private void handleServerMessage(String message) {
            switch (stage) {
                case WAITING_FOR_ALTS -> handleAltsMessage(message);
                case WAITING_FOR_HISTORY -> handleHistoryMessage(message);
            }
        }

        private void handleAltsMessage(String message) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (!lower.contains(ign.toLowerCase(Locale.ROOT))) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (lower.contains("no known alts") || lower.contains("no alts") || lower.contains("no other accounts")) {
                ChatUtils.sendClientMessage(client, PREFIX + "No known alts, checking history of §6" + ign + "§7 only.");
                beginHistoryChecks(client, List.of());
                return;
            }

            int colon = message.indexOf(':');
            if (colon < 0 || !lower.substring(0, colon).contains("alt")) {
                return;
            }

            List<String> alts = new ArrayList<>();
            Matcher matcher = USERNAME.matcher(message.substring(colon + 1));
            while (matcher.find()) {
                String name = matcher.group();
                if (!name.equalsIgnoreCase(ign) && alts.stream().noneMatch(name::equalsIgnoreCase)) {
                    alts.add(name);
                }
            }

            if (alts.isEmpty()) {
                return;
            }

            if (alts.size() > MAX_ACCOUNTS - 1) {
                ChatUtils.sendClientMessage(client, PREFIX + "Found §6" + alts.size() + "§7 alts, only checking the first §6"
                        + (MAX_ACCOUNTS - 1) + "§7.");
                alts = alts.subList(0, MAX_ACCOUNTS - 1);
            } else {
                ChatUtils.sendClientMessage(client, PREFIX + "Found §6" + alts.size() + "§7 possible alt"
                        + (alts.size() == 1 ? "" : "s") + ": §6" + String.join("§7, §6", alts) + "§7.");
            }

            beginHistoryChecks(client, alts);
        }

        private void handleHistoryMessage(String message) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("ban")) {
                currentStats.bans++;
            } else if (lower.contains("mute")) {
                currentStats.mutes++;
            } else if (lower.contains("warn")) {
                currentStats.warns++;
            } else if (lower.contains("kick")) {
                currentStats.kicks++;
            }
        }

        private void beginHistoryChecks(MinecraftClient client, List<String> alts) {
            if (altsParsed) {
                return;
            }
            altsParsed = true;

            remainingAccounts.add(ign);
            remainingAccounts.addAll(alts);
            sendNextHistoryCommand(client);
        }

        private void sendNextHistoryCommand(MinecraftClient client) {
            String next = remainingAccounts.poll();
            if (next == null) {
                report(client);
                finish();
                return;
            }

            currentStats = new PunishmentStats(next);
            waitTicks = 0;
            stage = Stage.WAITING_FOR_HISTORY;
            ClientUtils.sendCommand(client, "history " + next);
        }

        private void report(MinecraftClient client) {
            String header = PREFIX + "Alt history of §6" + ign + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (PunishmentStats stats : results) {
                MutableText line = Text.literal("§8• ").append(clickableName(stats.name)).append("§7: ");
                if (stats.isClean()) {
                    line.append("§aclean");
                } else {
                    List<String> parts = new ArrayList<>();
                    if (stats.bans > 0) {
                        parts.add("§cBans: " + stats.bans);
                    }
                    if (stats.mutes > 0) {
                        parts.add("§6Mutes: " + stats.mutes);
                    }
                    if (stats.warns > 0) {
                        parts.add("§eWarns: " + stats.warns);
                    }
                    if (stats.kicks > 0) {
                        parts.add("§7Kicks: " + stats.kicks);
                    }
                    line.append(String.join("§7, ", parts));
                }
                ChatUtils.sendClientMessage(client, line);
            }
            ChatUtils.sendClientMessage(client, "§8Counts are based on the /history output and may include unrelated lines.");
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private MutableText clickableName(String name) {
            return Text.literal("§6" + name).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/history " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Click to run §6/history " + name))));
        }

        private void cancel(MinecraftClient client, String reason) {
            ChatUtils.sendClientMessage(client, PREFIX + reason);
            finish();
        }

        private void finish() {
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
