package com.duperknight.client.modules;

import com.duperknight.client.gui.CheckAltsScreen;
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
    private static final int ALTS_OUTPUT_QUIET_TICKS = 10;
    private static final int HISTORY_WINDOW_TICKS = 20 * 3;
    private static final int MAX_ACCOUNTS = 10;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern USERNAME_LIST = Pattern.compile(
            "[A-Za-z0-9_]{3,16}(?:\\s*,\\s*[A-Za-z0-9_]{3,16})*");

    private CheckSession activeSession;

    public CheckAltsModule() {
        super(StaffRank.MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.check_alts.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.check_alts.description.1"),
                Text.translatable("dmls.module.check_alts.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new CheckAltsScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("dalts")
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

    /** Starts an alt check for the given player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return;
        }

        start(client, ign);
    }

    private void start(MinecraftClient client, String ign) {
        if (activeSession != null) {
            activeSession.cancel(client, "Started a new check for §6" + ign + "§7.");
        }

        activeSession = new CheckSession(ign);
        activeSession.start(client);
    }

    private void handleServerMessage(String message) {
        String cleanMessage = ChatUtils.cleanLine(message);
        // ClientPlayerEntity#sendMessage also fires the GAME receive event. Ignore
        // DMLS output so status messages cannot be parsed recursively as server output.
        if (cleanMessage.startsWith("[DMLS")) {
            return;
        }

        if (activeSession != null) {
            activeSession.handleServerMessage(cleanMessage);
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
        private boolean readingAltsList;
        private int altsListQuietTicks;
        private final List<String> collectedAlts = new ArrayList<>();

        private CheckSession(String ign) {
            this.ign = ign;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.checking", ign);
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
                    if (readingAltsList && ++altsListQuietTicks > ALTS_OUTPUT_QUIET_TICKS) {
                        finishAltsList(client);
                    } else if (!readingAltsList && waitTicks > ALTS_TIMEOUT_TICKS) {
                        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.read_failed", ign);
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (lower.contains(ign.toLowerCase(Locale.ROOT))
                    && (lower.contains("no known alts") || lower.contains("no alts") || lower.contains("no other accounts"))) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.no_alts", ign);
                beginHistoryChecks(client, List.of());
                return;
            }

            // The server's /alts command prints a status legend followed by one
            // username per chat line instead of a single "Alts: ..." line.
            if (isAltsStatusHeader(lower)) {
                readingAltsList = true;
                altsListQuietTicks = 0;
                return;
            }

            if (readingAltsList) {
                if (USERNAME_LIST.matcher(message).matches()) {
                    altsListQuietTicks = 0;
                    Matcher accounts = USERNAME.matcher(message);
                    while (accounts.find()) {
                        String name = accounts.group();
                        if (!name.equalsIgnoreCase(ign)
                                && collectedAlts.stream().noneMatch(name::equalsIgnoreCase)) {
                            collectedAlts.add(name);
                        }
                    }
                }
                return;
            }

            if (!lower.contains(ign.toLowerCase(Locale.ROOT))) {
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

            announceAltsAndBeginHistory(client, alts);
        }

        private boolean isAltsStatusHeader(String lower) {
            return lower.contains("[online]")
                    && lower.contains("[offline]")
                    && lower.contains("[banned]")
                    && lower.contains("[ipbanned]");
        }

        private void finishAltsList(MinecraftClient client) {
            if (collectedAlts.isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.no_alts", ign);
                beginHistoryChecks(client, List.of());
                return;
            }

            announceAltsAndBeginHistory(client, new ArrayList<>(collectedAlts));
        }

        private void announceAltsAndBeginHistory(MinecraftClient client, List<String> alts) {

            if (alts.size() > MAX_ACCOUNTS - 1) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_alts.found_limited", alts.size(), MAX_ACCOUNTS - 1);
                alts = alts.subList(0, MAX_ACCOUNTS - 1);
            } else {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        alts.size() == 1 ? "dmls.chat.check_alts.found.one" : "dmls.chat.check_alts.found.many",
                        alts.size(), String.join(", ", alts));
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
                // End the session before printing the report. Client-side chat
                // messages fire the receive event too, and result text such as
                // "Mutes: 1" must not be counted against the last account.
                finish();
                report(client);
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
            ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.check_alts.counts_note");
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private MutableText clickableName(String name) {
            return Text.literal("§6" + name).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/history " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.check_alts.hover_history", name))));
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
