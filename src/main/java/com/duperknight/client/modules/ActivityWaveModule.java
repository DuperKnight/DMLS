package com.duperknight.client.modules;

import com.duperknight.client.gui.ActivityWaveScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the recent activity of a whole staff wave with the server's /activity command. */
public final class ActivityWaveModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Activity§8] §7";
    private static final int COMMAND_GAP_TICKS = 20;
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;
    private static final int MAX_PLAYERS = 60;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern ACTIVITY_HEADER = Pattern.compile(
            "activity for player ([A-Za-z0-9_]{3,16}) in the last (\\d+) days", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTIVITY_HOURS = Pattern.compile(
            "([\\d,]+(?:\\.\\d+)?) minutes or ([\\d,]+(?:\\.\\d+)?) hours", Pattern.CASE_INSENSITIVE);

    private WaveSession activeSession;

    public ActivityWaveModule() {
        super(StaffRank.ADMIN);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.activity.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPASS);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.activity.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ActivityWaveScreen(parent, this));
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

    /** Starts an activity check for the given players. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String input) {
        if (!canRunPrivilegedOperation(client)) {
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
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    skipped.size() == 1 ? "dmls.chat.activity.skipping.one" : "dmls.chat.activity.skipping.many",
                    String.join(", ", skipped));
        }

        if (igns.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            return;
        }

        if (igns.size() > MAX_PLAYERS) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.too_many", MAX_PLAYERS);
            return;
        }

        if (activeSession != null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.active");
            return;
        }

        activeSession = new WaveSession(igns, ServerGuard.connectionIdentity(client));
        activeSession.start(client);
    }

    private void handleServerMessage(ServerMessage message) {
        if (activeSession != null) {
            activeSession.handleServerMessage(message.cleanText());
        }
    }

    private final class WaveSession {
        private final List<String> igns;
        private final String serverIdentity;
        private final Map<String, OptionalDouble> results = new LinkedHashMap<>();

        private int playerIndex = -1;
        private int waitTicks;
        private boolean headerSeen;
        private boolean inGap;
        private int reportedDays = 30;

        private WaveSession(List<String> igns, String serverIdentity) {
            this.igns = igns;
            this.serverIdentity = serverIdentity;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    igns.size() == 1 ? "dmls.chat.activity.start.one" : "dmls.chat.activity.start.many", igns.size());
            next(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client) || !serverIdentity.equals(ServerGuard.connectionIdentity(client))) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
                activeSession = null;
                return;
            }

            if (playerIndex < 0 || playerIndex >= igns.size()) {
                return;
            }

            waitTicks++;
            if (inGap) {
                if (waitTicks >= COMMAND_GAP_TICKS) {
                    next(client);
                }
            } else if (waitTicks > RESPONSE_TIMEOUT_TICKS) {
                results.put(currentIgn(), OptionalDouble.empty());
                next(client);
            }
        }

        private void handleServerMessage(String text) {
            if (playerIndex < 0 || playerIndex >= igns.size() || inGap) {
                return;
            }

            if (!headerSeen) {
                Matcher header = ACTIVITY_HEADER.matcher(text);
                if (header.find() && header.group(1).equalsIgnoreCase(currentIgn())) {
                    headerSeen = true;
                    reportedDays = Integer.parseInt(header.group(2));
                }
            }

            if (headerSeen) {
                Matcher hours = ACTIVITY_HOURS.matcher(text);
                if (hours.find()) {
                    parseNumber(hours.group(2)).ifPresent(value -> {
                        results.put(currentIgn(), OptionalDouble.of(value));
                        inGap = true;
                        waitTicks = 0;
                    });
                }
            }
        }

        private void next(MinecraftClient client) {
            playerIndex++;
            inGap = false;
            headerSeen = false;
            waitTicks = 0;

            if (playerIndex >= igns.size()) {
                report(client);
                activeSession = null;
                return;
            }

            String ign = currentIgn();
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.progress", ign, playerIndex + 1, igns.size());
            if (!ClientUtils.sendCommand(client, "activity " + ign)) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
                activeSession = null;
            }
        }

        private String currentIgn() {
            return igns.get(playerIndex);
        }

        private void report(MinecraftClient client) {
            String header = PREFIX;
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.header", reportedDays);

            results.entrySet().stream()
                    .sorted(Comparator.comparingDouble((Map.Entry<String, OptionalDouble> entry) ->
                            entry.getValue().orElse(-1)).reversed())
                    .forEach(entry -> {
                        if (entry.getValue().isPresent()) {
                            ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(
                                    "dmls.chat.activity.result", entry.getKey(),
                                    String.format(Locale.ROOT, "%.1f", entry.getValue().getAsDouble()))));
                        } else {
                            ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(
                                    "dmls.chat.activity.no_data", entry.getKey())));
                        }
                    });
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private Optional<Double> parseNumber(String value) {
            try {
                return Optional.of(Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
    }
}
