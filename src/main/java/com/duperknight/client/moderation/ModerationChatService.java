package com.duperknight.client.moderation;

import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.MojangProfileLookup;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pre-filter, bounded live-chat capture and lazy player identity resolver. */
public final class ModerationChatService {
    public static final int MAX_MESSAGES = 1_000;
    private static final int REALNAME_TIMEOUT_TICKS = 60;
    private static final long CROSS_EVENT_DUPLICATE_NANOS = 250_000_000L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern MSG_SUGGESTION = Pattern.compile("^/?msg\\s+([A-Za-z0-9_]{3,16})(?:\\s.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKETED_REALNAME_RESPONSE = Pattern.compile(
            "^\\[([^]\\r\\n]{1,32})]\\s+is\\s+\\[([A-Za-z0-9_]{3,16})]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_REALNAME_RESPONSE = Pattern.compile(
            "^([^\\r\\n]{1,32}?)\\s+is\\s+([A-Za-z0-9_]{3,16})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VISIBLE_USERNAME = Pattern.compile("[^\\s|:»]{1,32}");
    private static final Pattern PRIVATE_STAFF_LINE = Pattern.compile(
            "^\\[(?:SC|AC)]\\s+\\[[^]\\r\\n]{1,32}]\\s+([^\\s|:»]{1,32})\\s*:\\s*(.+)$");

    private static final Deque<ModerationMessage> MESSAGES = new ArrayDeque<>(MAX_MESSAGES);
    private static final Map<String, String> IGN_BY_VISIBLE_NAME = new HashMap<>();
    private static final Map<String, UUID> UUID_BY_IGN = new HashMap<>();
    private static final Deque<RealnameRequest> REALNAME_QUEUE = new ArrayDeque<>();
    private static final ChannelMentionTracker CHANNEL_MENTIONS = new ChannelMentionTracker();

    private static RealnameRequest activeRealname;
    private static int activeRealnameTicks;
    private static long nextSequence;
    private static long revision;
    private static String previousText = "";
    private static boolean previousWasPlayerEvent;
    private static long previousAt;
    private static boolean registered;

    private ModerationChatService() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) -> {
            if (ModerationMessageSuppression.consume(message)) return true;
            capture(message, sender, true, false);
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (ModerationMessageSuppression.consume(message)) return true;
            if (handleRealnameResponse(message)) return false;
            if (!overlay) capture(message, null, false, false);
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ModerationChatService::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static synchronized List<ModerationMessage> messages() {
        return List.copyOf(MESSAGES);
    }

    public static synchronized long revision() {
        return revision;
    }

    static ChannelMentionTracker channelMentions() {
        return CHANNEL_MENTIONS;
    }

    static synchronized void capture(Text text, GameProfile sender, boolean playerEvent, boolean overlay) {
        if (text == null || overlay) return;
        String clean = ChatUtils.cleanLine(text.getString());
        if (clean.isEmpty()) return;
        PunishmentLogService.recordChatLine(clean);
        long now = System.nanoTime();
        if (isCrossEventDuplicate(clean, playerEvent, now)) return;

        ParsedPlayerLine parsed = parsePlayerLine(clean).orElse(null);
        boolean playerMessage = parsed != null;
        ChatChannel channel = playerMessage ? ChatChannel.classifyPlayerLine(clean) : ChatChannel.SERVER;
        String visible = playerMessage ? parsed.visibleUsername() : "";
        String body = playerMessage ? parsed.messageBody() : clean;
        String clickIgn = extractIgnFromClickMetadata(text).orElse(null);
        String capturedIgn = channel.visibleUsernameIsIgn() && InputValidators.isUsername(visible)
                ? visible
                : clickIgn != null ? clickIgn
                : sender != null && InputValidators.isUsername(sender.name()) ? sender.name() : null;
        UUID capturedUuid = sender == null ? null : sender.id();
        if (capturedIgn != null) {
            IGN_BY_VISIBLE_NAME.put(normalize(visible), capturedIgn);
            if (capturedUuid != null) UUID_BY_IGN.put(normalize(capturedIgn), capturedUuid);
        }

        ModerationMessage message = new ModerationMessage(++nextSequence, LocalTime.now().format(TIME), text, clean,
                channel, playerMessage, visible, body, capturedIgn, capturedUuid);
        if (MESSAGES.size() == MAX_MESSAGES) MESSAGES.removeFirst();
        MESSAGES.addLast(message);
        revision++;
    }

    static Optional<ParsedPlayerLine> parsePlayerLine(String clean) {
        if (clean == null) return Optional.empty();
        Matcher staffMatcher = PRIVATE_STAFF_LINE.matcher(clean);
        if (staffMatcher.matches()) {
            String body = staffMatcher.group(2).trim();
            if (!body.isEmpty()) return Optional.of(new ParsedPlayerLine(staffMatcher.group(1), body));
        }
        int divider = clean.indexOf('|');
        int separator = divider < 0 ? -1 : playerMessageSeparator(clean, divider + 1);
        if (divider < 0 || separator < 0) return Optional.empty();
        String visible = clean.substring(divider + 1, separator).trim();
        String body = clean.substring(separator + 1).trim();
        if (!VISIBLE_USERNAME.matcher(visible).matches() || body.isEmpty()) return Optional.empty();
        return Optional.of(new ParsedPlayerLine(visible, body));
    }

    private static int playerMessageSeparator(String clean, int fromIndex) {
        // Prefixed channel formats use both "Name : message" and "Name: message". Global/server output
        // keeps the stricter spaced-colon rule so lines such as "Sales: 0 | Net: 0 Coins" stay non-player.
        int colon = ChatChannel.classifyPlayerLine(clean) == ChatChannel.GLOBAL
                ? whitespacePrefixedColon(clean, fromIndex)
                : clean.indexOf(':', fromIndex);
        int chevron = clean.indexOf('»', fromIndex);
        if (colon < 0) return chevron;
        if (chevron < 0) return colon;
        return Math.min(colon, chevron);
    }

    private static int whitespacePrefixedColon(String clean, int fromIndex) {
        int colon = clean.indexOf(':', fromIndex);
        while (colon >= 0) {
            if (colon > fromIndex && Character.isWhitespace(clean.charAt(colon - 1))) return colon;
            colon = clean.indexOf(':', colon + 1);
        }
        return -1;
    }

    static Optional<String> parseRealnameResponse(String requestedVisibleName, String cleanLine) {
        if (requestedVisibleName == null || cleanLine == null) return Optional.empty();
        String response = cleanLine.trim();
        Matcher bracketed = BRACKETED_REALNAME_RESPONSE.matcher(response);
        if (bracketed.matches()) return correlatedRealname(requestedVisibleName, bracketed);
        Matcher plain = PLAIN_REALNAME_RESPONSE.matcher(response);
        return plain.matches() ? correlatedRealname(requestedVisibleName, plain) : Optional.empty();
    }

    private static Optional<String> correlatedRealname(String requestedVisibleName, Matcher matcher) {
        return matcher.group(1).equalsIgnoreCase(requestedVisibleName)
                ? Optional.of(matcher.group(2))
                : Optional.empty();
    }

    static Optional<String> extractIgnFromClickMetadata(Text text) {
        if (text == null) return Optional.empty();
        return text.visit((style, value) -> ignFromStyle(style), Style.EMPTY);
    }

    private static Optional<String> ignFromStyle(Style style) {
        if (style.getClickEvent() instanceof ClickEvent.SuggestCommand suggestion) {
            Matcher matcher = MSG_SUGGESTION.matcher(suggestion.command().trim());
            if (matcher.matches()) return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    public static synchronized Optional<String> knownIgn(ModerationMessage message) {
        if (message == null || !message.playerMessage()) return Optional.empty();
        return message.capturedIgnOptional().or(() ->
                Optional.ofNullable(IGN_BY_VISIBLE_NAME.get(normalize(message.visibleUsername()))));
    }

    public static void resolveIgn(MinecraftClient client, ModerationMessage message,
                                  Consumer<Optional<String>> callback) {
        Optional<String> known = knownIgn(message);
        if (known.isPresent() || message == null || !message.playerMessage() || DMLSConfig.dryRun()) {
            callback.accept(known);
            return;
        }
        synchronized (ModerationChatService.class) {
            REALNAME_QUEUE.addLast(new RealnameRequest(message.visibleUsername(), callback));
            startNextRealname(client);
        }
    }

    public static void resolveUuid(MinecraftClient client, ModerationMessage message,
                                   Consumer<Optional<UUID>> callback) {
        resolveIgn(client, message, ign -> {
            if (ign.isEmpty()) {
                callback.accept(Optional.empty());
                return;
            }
            UUID cached;
            synchronized (ModerationChatService.class) {
                cached = UUID_BY_IGN.get(normalize(ign.get()));
            }
            if (cached != null) {
                callback.accept(Optional.of(cached));
                return;
            }
            MojangProfileLookup.lookup(List.of(ign.get())).whenComplete((result, error) -> client.execute(() -> {
                if (error != null || result.status() != MojangProfileLookup.Status.SUCCESS
                        || result.entries().isEmpty() || !result.entries().getFirst().found()) {
                    callback.accept(Optional.empty());
                    return;
                }
                try {
                    UUID uuid = UUID.fromString(result.entries().getFirst().uuid());
                    synchronized (ModerationChatService.class) {
                        UUID_BY_IGN.put(normalize(ign.get()), uuid);
                    }
                    callback.accept(Optional.of(uuid));
                } catch (IllegalArgumentException exception) {
                    callback.accept(Optional.empty());
                }
            }));
        });
    }

    private static synchronized boolean handleRealnameResponse(Text text) {
        if (activeRealname == null || text == null) return false;
        Optional<String> parsed = parseRealnameResponse(activeRealname.visibleUsername(),
                ChatUtils.cleanLine(text.getString()));
        if (parsed.isEmpty()) return false;
        String ign = parsed.get();
        IGN_BY_VISIBLE_NAME.put(normalize(activeRealname.visibleUsername()), ign);
        Consumer<Optional<String>> callback = activeRealname.callback();
        activeRealname = null;
        activeRealnameTicks = 0;
        callback.accept(Optional.of(ign));
        startNextRealname(MinecraftClient.getInstance());
        return true;
    }

    private static synchronized void startNextRealname(MinecraftClient client) {
        if (activeRealname != null || REALNAME_QUEUE.isEmpty()) return;
        if (DMLSConfig.dryRun()) {
            RealnameRequest blocked = REALNAME_QUEUE.removeFirst();
            blocked.callback().accept(Optional.empty());
            startNextRealname(client);
            return;
        }
        RealnameRequest next = REALNAME_QUEUE.removeFirst();
        String cached = IGN_BY_VISIBLE_NAME.get(normalize(next.visibleUsername()));
        if (cached != null) {
            next.callback().accept(Optional.of(cached));
            startNextRealname(client);
            return;
        }
        CommandDispatch dispatch = ClientUtils.dispatchCommand(client, "realname " + next.visibleUsername(), false,
                ConnectionSnapshot.capture(client));
        if (dispatch != CommandDispatch.SENT) {
            next.callback().accept(Optional.empty());
            startNextRealname(client);
            return;
        }
        activeRealname = next;
        activeRealnameTicks = REALNAME_TIMEOUT_TICKS;
    }

    private static void tick(MinecraftClient client) {
        Consumer<Optional<String>> expired = null;
        synchronized (ModerationChatService.class) {
            if (activeRealname != null && --activeRealnameTicks <= 0) {
                expired = activeRealname.callback();
                activeRealname = null;
                activeRealnameTicks = 0;
            }
        }
        if (expired != null) {
            expired.accept(Optional.empty());
            synchronized (ModerationChatService.class) {
                startNextRealname(client);
            }
        }
    }

    static synchronized boolean isCrossEventDuplicate(String clean, boolean playerEvent, long now) {
        boolean duplicate = playerEvent != previousWasPlayerEvent && clean.equals(previousText)
                && now - previousAt <= CROSS_EVENT_DUPLICATE_NANOS;
        previousText = clean;
        previousWasPlayerEvent = playerEvent;
        previousAt = now;
        return duplicate;
    }

    private static synchronized void reset() {
        List<Consumer<Optional<String>>> callbacks = new ArrayList<>();
        if (activeRealname != null) callbacks.add(activeRealname.callback());
        REALNAME_QUEUE.forEach(request -> callbacks.add(request.callback()));
        MESSAGES.clear();
        IGN_BY_VISIBLE_NAME.clear();
        UUID_BY_IGN.clear();
        REALNAME_QUEUE.clear();
        activeRealname = null;
        activeRealnameTicks = 0;
        previousText = "";
        previousAt = 0;
        nextSequence = 0;
        CHANNEL_MENTIONS.reset();
        revision++;
        callbacks.forEach(callback -> callback.accept(Optional.empty()));
    }

    static synchronized void resetForTests() {
        reset();
        previousWasPlayerEvent = false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    record ParsedPlayerLine(String visibleUsername, String messageBody) {
    }

    private record RealnameRequest(String visibleUsername, Consumer<Optional<String>> callback) {
    }
}
