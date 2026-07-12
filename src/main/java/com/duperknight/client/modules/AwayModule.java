package com.duperknight.client.modules;

import com.duperknight.client.gui.AwayScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.utils.CannedReplies;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Automatically replies to private messages while the user is away or busy. */
public final class AwayModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Away§8] §7";
    private static final long SENDER_COOLDOWN_MILLIS = 5 * 60 * 1000;
    private static final long GLOBAL_REPLY_GAP_MILLIS = 2 * 1000;
    private static final long MAX_BRB_MILLIS = 24L * 60 * 60 * 1000;
    private static final Pattern DURATION_PART = Pattern.compile("(\\d{1,5})([hms])");
    private static final Pattern BARE_MINUTES = Pattern.compile("\\d{1,5}");
    private static final List<Pattern> INCOMING_WHISPERS = List.of(
            Pattern.compile("^\\[([A-Za-z0-9_]{3,16}) *(?:->|→) *(?:me|you)\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([A-Za-z0-9_]{3,16}) whispers(?: to you)?:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^From ([A-Za-z0-9_]{3,16}) *[:»>]", Pattern.CASE_INSENSITIVE)
    );

    public enum Mode {
        OFF,
        BRB,
        DND
    }

    private Mode mode = Mode.OFF;
    private long brbExpiresAtMillis;
    private long lastReplyAtMillis;
    private final Map<String, Long> lastReplyBySender = new HashMap<>();
    private final List<String> sendersWhileAway = new ArrayList<>();

    public AwayModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.away.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CLOCK);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.away.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new AwayScreen(parent, this));
    }

    @Override
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.PLAYER_CHAT, MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    public Mode mode() {
        return mode;
    }

    /** Enables BRB mode for the given duration, like 5m, 30s, 1h or 1h30m. */
    public void startBrb(MinecraftClient client, String durationInput) {
        OptionalLong duration = parseDurationMillis(durationInput);
        if (duration.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.brb.invalid");
            return;
        }

        mode = Mode.BRB;
        brbExpiresAtMillis = System.currentTimeMillis() + duration.getAsLong();
        clearAwayState();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.brb.on", formatDuration(duration.getAsLong()));
    }

    /** Enables or disables DND mode. */
    public void setDnd(MinecraftClient client, boolean enabled) {
        if (!enabled) {
            disable(client);
            return;
        }

        mode = Mode.DND;
        clearAwayState();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.dnd.on");
    }

    /** Disables any away mode and reports who messaged meanwhile. */
    public void disable(MinecraftClient client) {
        if (mode == Mode.OFF) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.already_off");
            return;
        }

        mode = Mode.OFF;
        if (!sendersWhileAway.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    sendersWhileAway.size() == 1 ? "dmls.chat.away.summary.one" : "dmls.chat.away.summary.many",
                    sendersWhileAway.size(), String.join(", ", sendersWhileAway));
        }
        clearAwayState();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.off");
    }

    /** Reports the current away status in chat. */
    public void status(MinecraftClient client) {
        switch (mode) {
            case OFF -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.status.off");
            case BRB -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.status.brb",
                    formatDuration(Math.max(0, brbExpiresAtMillis - System.currentTimeMillis())));
            case DND -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.status.dnd");
        }
    }

    /** The status line shown on the module screen. */
    public Text statusText() {
        return switch (mode) {
            case OFF -> Text.translatable("dmls.screen.away.status.off");
            case BRB -> Text.translatable("dmls.screen.away.status.brb",
                    formatDuration(Math.max(0, brbExpiresAtMillis - System.currentTimeMillis())));
            case DND -> Text.translatable("dmls.screen.away.status.dnd");
        };
    }

    /** Parses durations like 5m, 30s, 1h or 1h30m into milliseconds. Bare numbers count as minutes. */
    public static OptionalLong parseDurationMillis(String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return OptionalLong.empty();
        }

        if (BARE_MINUTES.matcher(trimmed).matches()) {
            return clampDuration(Long.parseLong(trimmed) * 60 * 1000);
        }

        Matcher matcher = DURATION_PART.matcher(trimmed);
        long totalMillis = 0;
        int matchedLength = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            totalMillis += switch (matcher.group(2)) {
                case "h" -> value * 60 * 60 * 1000;
                case "m" -> value * 60 * 1000;
                default -> value * 1000;
            };
            matchedLength += matcher.group().length();
        }

        if (totalMillis <= 0 || matchedLength != trimmed.length()) {
            return OptionalLong.empty();
        }
        return clampDuration(totalMillis);
    }

    private static OptionalLong clampDuration(long millis) {
        return millis <= 0 ? OptionalLong.empty() : OptionalLong.of(Math.min(millis, MAX_BRB_MILLIS));
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(1, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            result.append(hours).append("h");
        }
        if (minutes > 0) {
            result.append(minutes).append("m");
        }
        if (seconds > 0 && hours == 0) {
            result.append(seconds).append("s");
        }
        return result.isEmpty() ? "0s" : result.toString();
    }

    private void tick(MinecraftClient client) {
        if (mode == Mode.BRB && System.currentTimeMillis() > brbExpiresAtMillis) {
            disable(client);
        }
    }

    private void handleServerMessage(ServerMessage message) {
        if (mode == Mode.OFF) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        Optional<String> parsedSender = parseIncomingWhisper(message.cleanText());
        if (parsedSender.isEmpty()) {
            return;
        }

        String sender = parsedSender.get();
        String ownName = client.player.getName().getString();
        if (sender.equalsIgnoreCase(ownName)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReplyAtMillis < GLOBAL_REPLY_GAP_MILLIS) {
            return;
        }

        Long lastReply = lastReplyBySender.get(sender.toLowerCase(Locale.ROOT));
        if (lastReply != null && now - lastReply < SENDER_COOLDOWN_MILLIS) {
            return;
        }

        // Never auto-send commands on servers outside the allowlist.
        if (!ServerGuard.check(client).allowed()) {
            return;
        }

        Optional<String> reply = CannedReplies.get(mode == Mode.BRB ? "brb" : "busy");
        if (reply.isEmpty()) {
            return;
        }

        if (ClientUtils.sendCommand(client, "msg %s %s".formatted(sender, reply.get()))) {
            lastReplyAtMillis = now;
            lastReplyBySender.put(sender.toLowerCase(Locale.ROOT), now);
            if (sendersWhileAway.stream().noneMatch(sender::equalsIgnoreCase)) {
                sendersWhileAway.add(sender);
            }
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.away.replied", sender);
        }
    }

    private static Optional<String> parseIncomingWhisper(String message) {
        for (Pattern pattern : INCOMING_WHISPERS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private void clearAwayState() {
        lastReplyBySender.clear();
        sendersWhileAway.clear();
        lastReplyAtMillis = 0;
    }
}
