package com.duperknight.client.modules;

import com.duperknight.client.gui.ContainerScanScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scans container activity with CoreProtect and summarizes who took and added what. */
public final class ContainerScanModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Containers§8] §7";
    private static final int PAGE_TIMEOUT_TICKS = 20 * 10;
    private static final int SILENCE_FINISH_TICKS = 20 * 3;
    private static final int PAGE_GAP_TICKS = 20;
    private static final int MAX_ITEMS_PER_LINE = 8;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern TIME = Pattern.compile("(\\d{1,4}[wdhms])+");
    private static final Pattern RADIUS = Pattern.compile("#global|\\d{1,5}");
    private static final Pattern ENTRY = Pattern.compile(
            "([A-Za-z0-9_]{3,16}) (removed|added) x?(\\d+) (?:minecraft:)?([a-z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("page (\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_RESULTS = Pattern.compile("no (?:lookup )?results", Pattern.CASE_INSENSITIVE);

    private ScanSession activeSession;

    public ContainerScanModule() {
        super(StaffRank.MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.containers.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.containers.description.1"),
                Text.translatable("dmls.module.containers.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ContainerScanScreen(parent, this));
    }

    @Override
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    /** Starts a container scan. Use * as the IGN to include every player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign, String time, String radius) {
        if (!canRunPrivilegedOperation(client)) {
            return;
        }

        String cleanIgn = ign.trim();
        boolean allPlayers = cleanIgn.equals("*") || cleanIgn.isEmpty();
        if (!allPlayers && !USERNAME.matcher(cleanIgn).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return;
        }

        String cleanTime = time.trim().toLowerCase(Locale.ROOT);
        if (!TIME.matcher(cleanTime).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.co.time");
            return;
        }

        String cleanRadius = radius.trim().toLowerCase(Locale.ROOT);
        if (!RADIUS.matcher(cleanRadius).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.co.radius");
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.active");
            return;
        }

        String command = "co lookup" + (allPlayers ? "" : " u:" + cleanIgn)
                + " t:" + cleanTime + " r:" + cleanRadius + " a:container";
        if (!ClientUtils.sendCommand(client, command)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return;
        }

        if (DMLSConfig.dryRun()) {
            return;
        }

        activeSession = new ScanSession(allPlayers ? "*" : cleanIgn, cleanTime, cleanRadius);
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.start", cleanTime, cleanRadius, "/dmls containers cancel");
    }

    private void handleServerMessage(ServerMessage message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(message.cleanText());
        }
    }

    /** Cancels an in-progress scan and reports what was collected so far. */
    public void cancel(MinecraftClient client) {
        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.cancelled");
            activeSession.report(client);
            activeSession = null;
        } else {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.nothing");
        }
    }

    private final class ScanSession {
        private final String ign;
        private final String time;
        private final String radius;
        private final String serverIdentity;
        // player -> action -> item -> count
        private final Map<String, Map<String, Map<String, Long>>> results = new LinkedHashMap<>();

        private int waitTicks;
        private int currentPage = 1;
        private int totalPages = 1;
        private boolean pageMarkerSeen;
        private boolean dataSeen;

        private ScanSession(String ign, String time, String radius) {
            this.ign = ign;
            this.time = time;
            this.radius = radius;
            this.serverIdentity = ServerGuard.connectionIdentity(MinecraftClient.getInstance());
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client) || !serverIdentity.equals(ServerGuard.connectionIdentity(client))) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
                activeSession = null;
                return;
            }

            waitTicks++;
            if (pageMarkerSeen && waitTicks >= PAGE_GAP_TICKS) {
                if (currentPage < totalPages) {
                    currentPage++;
                    pageMarkerSeen = false;
                    waitTicks = 0;
                    if (currentPage % 5 == 0) {
                        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.progress", currentPage, totalPages);
                    }
                    if (!ClientUtils.sendCommand(client, "co page " + currentPage)) {
                        report(client);
                        activeSession = null;
                    }
                } else {
                    report(client);
                    activeSession = null;
                }
            } else if (dataSeen && !pageMarkerSeen && waitTicks > SILENCE_FINISH_TICKS) {
                report(client);
                activeSession = null;
            } else if (waitTicks > PAGE_TIMEOUT_TICKS) {
                if (!dataSeen) {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.no_response");
                } else {
                    report(client);
                }
                activeSession = null;
            }
        }

        private void handleServerMessage(String text) {
            if (NO_RESULTS.matcher(text).find()) {
                ChatUtils.sendTranslatedMessage(MinecraftClient.getInstance(), PREFIX, "dmls.chat.containers.no_results");
                activeSession = null;
                return;
            }

            Matcher entry = ENTRY.matcher(text);
            while (entry.find()) {
                String player = entry.group(1);
                if (!ign.equals("*") && !player.equalsIgnoreCase(ign)) {
                    continue;
                }
                String action = entry.group(2).toLowerCase(Locale.ROOT);
                long count = Long.parseLong(entry.group(3));
                String item = entry.group(4).toLowerCase(Locale.ROOT);
                results.computeIfAbsent(player, key -> new LinkedHashMap<>())
                        .computeIfAbsent(action, key -> new LinkedHashMap<>())
                        .merge(item, count, Long::sum);
                dataSeen = true;
                waitTicks = 0;
            }

            Matcher page = PAGE.matcher(text);
            if (page.find()) {
                totalPages = Math.max(totalPages, Integer.parseInt(page.group(2)));
                pageMarkerSeen = true;
                waitTicks = 0;
            }
        }

        private void report(MinecraftClient client) {
            String header = Text.translatable("dmls.chat.containers.header", ign, time, radius).getString();
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));

            if (results.isEmpty()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.no_results");
            } else {
                results.entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, Map<String, Map<String, Long>>> entry) ->
                                entry.getValue().values().stream().flatMap(items -> items.values().stream())
                                        .mapToLong(Long::longValue).sum()).reversed())
                        .forEach(playerEntry -> {
                            Map<String, Map<String, Long>> actions = playerEntry.getValue();
                            if (actions.containsKey("removed")) {
                                ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(
                                        "dmls.chat.containers.removed", playerEntry.getKey(), itemsSummary(actions.get("removed")))));
                            }
                            if (actions.containsKey("added")) {
                                ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(
                                        "dmls.chat.containers.added", playerEntry.getKey(), itemsSummary(actions.get("added")))));
                            }
                        });
            }

            int scannedPages = currentPage;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.containers.pages", scannedPages, totalPages);
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private String itemsSummary(Map<String, Long> items) {
            List<String> parts = new ArrayList<>();
            items.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(MAX_ITEMS_PER_LINE)
                    .forEach(entry -> parts.add("x" + entry.getValue() + " " + entry.getKey()));
            int remaining = items.size() - MAX_ITEMS_PER_LINE;
            if (remaining > 0) {
                parts.add(Text.translatable("dmls.chat.containers.more", remaining).getString());
            }
            return String.join(", ", parts);
        }
    }
}
