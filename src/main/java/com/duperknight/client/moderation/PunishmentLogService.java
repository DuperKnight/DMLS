package com.duperknight.client.moderation;

import com.duperknight.DMLS;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rank-aware LiteBans scraper merged with a bounded, chat-updated session log. */
public final class PunishmentLogService implements PunishmentLogSource {
    public static final int MAX_ENTRIES = 40;

    private static final String BASE_URL = "https://stoneworks.gg/bans/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter WEBSITE_TIME =
            DateTimeFormatter.ofPattern("MMMM d, uuuu, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm:ss", Locale.ENGLISH);
    static final long HELPER_REFRESH_INTERVAL_MILLIS = 5 * 60_000L;
    private static final long DUPLICATE_WINDOW_MILLIS = 5_000L;
    private static final long WEBSITE_DUPLICATE_TOLERANCE_MILLIS = 2 * 60_000L;
    private static final String USERNAME = "[A-Za-z0-9_]{1,16}";
    private static final List<Page> PAGES = List.of(
            new Page(PunishmentType.BAN, "bans.php"),
            new Page(PunishmentType.MUTE, "mutes.php"),
            new Page(PunishmentType.WARNING, "warnings.php"),
            new Page(PunishmentType.KICK, "kicks.php")
    );
    private static final Pattern TABLE_BODY = Pattern.compile(
            "(?is)<tbody\\b[^>]*>(.*?)(?:</tbody>|</table>)");
    private static final Pattern TABLE_ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>");
    private static final Pattern PLAYER_UUID = Pattern.compile(
            "(?i)cravatar\\.eu/avatar/([0-9a-f]{32})(?:[/\"'])");
    private static final Pattern AVATAR_URL = Pattern.compile(
            "(?i)(https://cravatar\\.eu/avatar/[0-9a-f]{32}/[0-9]+)");
    private static final Pattern DETAILS_URL = Pattern.compile(
            "(?i)href\\s*=\\s*['\"]([^'\"]*info\\.php[^'\"]*)['\"]");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STAFF_ACTION = Pattern.compile(
            "^(?:\\[Silent]\\s+)?(" + USERNAME + "|Console)\\s+"
                    + "(tempbanip|banip|temp\\s+IP[- ]?banned|IP[- ]?banned|tempbanned|banned|"
                    + "temporarily\\s+(?:IP[- ]?)?banned|tempmuted|muted|temporarily\\s+muted|warned)\\s+"
                    + "(" + USERNAME + ")\\s+for\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KICK_ACTION = Pattern.compile(
            "^(?:\\[Silent]\\s+)?(" + USERNAME + ")\\s+was\\s+kicked\\s+by\\s+"
                    + "(" + USERNAME + "|Console)\\s+for\\s+(.+?)\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSIVE_WARNING = Pattern.compile(
            "^(?:\\[Silent]\\s+)?(" + USERNAME + ")\\s+was\\s+warned\\s+by\\s+"
                    + "(" + USERNAME + "|Console)\\s+for\\s+(.+?)\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final PunishmentLogService INSTANCE = new PunishmentLogService();

    private final EnumMap<PunishmentType, List<PunishmentLogEntry>> websiteEntries =
            new EnumMap<>(PunishmentType.class);
    private final Deque<LocalEntry> localEntries = new ArrayDeque<>();
    private final Map<String, Long> recentBroadcasts = new HashMap<>();

    private long lastRefreshAttemptMillis;
    private CompletableFuture<Void> refreshInFlight;
    private boolean registered;

    private PunishmentLogService() {
        for (PunishmentType type : PunishmentType.values()) {
            websiteEntries.put(type, List.of());
        }
    }

    public static PunishmentLogService shared() {
        return INSTANCE;
    }

    public static synchronized void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.currentScreen instanceof ModerationScreen) {
                INSTANCE.refreshIfStale();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.resetSession());
    }

    @Override
    public List<PunishmentLogEntry> latest() {
        synchronized (this) {
            List<PunishmentLogEntry> local = localEntries.stream().map(LocalEntry::entry).toList();
            List<PunishmentLogEntry> website = new ArrayList<>();
            for (Page page : PAGES) {
                website.addAll(websiteEntries.get(page.type()));
            }
            return mergeEntries(local, website);
        }
    }

    public synchronized void recordLocal(MinecraftClient client, PunishmentRequest request) {
        if (request == null) return;
        long now = System.currentTimeMillis();
        String staffName = client != null && client.player != null
                ? client.player.getName().getString()
                : "You";
        String duration = request.type().durationRequired() ? displayCommandDuration(request.duration()) : "";
        PunishmentLogEntry entry = new PunishmentLogEntry(request.type(), request.ign(),
                offlineUuid(request.ign()), staffName, request.reason(), duration, currentLogTime(),
                "", PunishmentLogOrigin.COMMAND, "", "", now);
        addLocal(entry, now, LocalSource.COMMAND);
    }

    static boolean recordChatLine(String cleanLine) {
        return INSTANCE.recordBroadcast(cleanLine, System.currentTimeMillis());
    }

    private synchronized boolean recordBroadcast(String cleanLine, long now) {
        Optional<PunishmentLogEntry> parsed = parseBroadcast(cleanLine);
        if (parsed.isEmpty()) return false;

        String fingerprint = WHITESPACE.matcher(cleanLine.replaceFirst("(?i)^\\[Silent]\\s+", ""))
                .replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        recentBroadcasts.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_WINDOW_MILLIS);
        Long previous = recentBroadcasts.put(fingerprint, now);
        if (previous != null && now - previous <= DUPLICATE_WINDOW_MILLIS) return false;
        EntryKey key = EntryKey.of(parsed.get());
        Iterator<LocalEntry> iterator = localEntries.iterator();
        while (iterator.hasNext()) {
            LocalEntry local = iterator.next();
            if (now - local.createdAtMillis() > DUPLICATE_WINDOW_MILLIS) break;
            if (local.source() == LocalSource.COMMAND && EntryKey.of(local.entry()).equals(key)) {
                iterator.remove();
                localEntries.addFirst(new LocalEntry(local.entry().confirmedBy(parsed.get()), now, LocalSource.CHAT));
                return false;
            }
        }
        addLocal(parsed.get(), now, LocalSource.CHAT);
        return true;
    }

    private void addLocal(PunishmentLogEntry entry, long now, LocalSource source) {
        localEntries.addFirst(new LocalEntry(entry, now, source));
        while (localEntries.size() > MAX_ENTRIES) localEntries.removeLast();
    }

    public void onScreenOpened() {
        synchronized (this) {
            lastRefreshAttemptMillis = 0L;
        }
        refreshIfStale();
    }

    private void refreshIfStale() {
        List<CompletableFuture<PageResult>> requests;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (refreshInFlight != null
                    || now - lastRefreshAttemptMillis < HELPER_REFRESH_INTERVAL_MILLIS) return;
            lastRefreshAttemptMillis = now;
            requests = PAGES.stream().map(this::fetch).toList();
            CompletableFuture<Void> combined = CompletableFuture.allOf(
                    requests.toArray(CompletableFuture[]::new)).thenRun(() -> applyResults(requests));
            refreshInFlight = combined;
            combined.whenComplete((ignored, error) -> {
                synchronized (this) {
                    if (refreshInFlight == combined) refreshInFlight = null;
                }
            });
        }
    }

    private CompletableFuture<PageResult> fetch(Page page) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + page.path()))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html")
                .header("User-Agent", "DuperKnight/DMLS LiteBans punishment log")
                .GET()
                .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        DMLS.LOGGER.warn("Could not fetch the LiteBans {} page", page.type(), error);
                        return PageResult.failed(page.type());
                    }
                    if (response.statusCode() != 200) {
                        DMLS.LOGGER.warn("LiteBans {} page returned HTTP {}", page.type(), response.statusCode());
                        return PageResult.failed(page.type());
                    }
                    List<PunishmentLogEntry> parsed = parsePage(page.type(), response.body());
                    if (parsed.isEmpty()) {
                        DMLS.LOGGER.warn("LiteBans {} page contained no readable punishment rows", page.type());
                        return PageResult.failed(page.type());
                    }
                    return PageResult.success(page.type(), parsed);
                });
    }

    private synchronized void applyResults(List<CompletableFuture<PageResult>> requests) {
        for (CompletableFuture<PageResult> request : requests) {
            PageResult result = request.join();
            if (result.success()) {
                websiteEntries.put(result.type(), preserveWebsiteHighlightTimes(
                        websiteEntries.get(result.type()), result.entries()));
            }
        }
    }

    private synchronized void resetSession() {
        if (refreshInFlight != null) refreshInFlight.cancel(true);
        refreshInFlight = null;
        localEntries.clear();
        recentBroadcasts.clear();
        for (PunishmentType type : PunishmentType.values()) {
            websiteEntries.put(type, List.of());
        }
        lastRefreshAttemptMillis = 0L;
    }

    static Optional<PunishmentLogEntry> parseBroadcast(String cleanLine) {
        if (cleanLine == null || cleanLine.isBlank()) return Optional.empty();
        String clean = cleanLine.trim();

        Matcher actionMatcher = STAFF_ACTION.matcher(clean);
        if (actionMatcher.matches()) {
            String action = actionMatcher.group(2).toLowerCase(Locale.ROOT);
            PunishmentType type = action.contains("ban")
                    ? PunishmentType.BAN
                    : action.contains("mute") ? PunishmentType.MUTE : PunishmentType.WARNING;
            String playerName = actionMatcher.group(3);
            String tail = actionMatcher.group(4).trim();
            boolean temporary = action.contains("temp");
            String duration = "";
            String reason = tail;
            if (temporary) {
                int reasonDivider = tail.toLowerCase(Locale.ROOT).indexOf(" for ");
                if (reasonDivider > 0) {
                    duration = tail.substring(0, reasonDivider).trim();
                    reason = tail.substring(reasonDivider + 5);
                }
            } else if (type == PunishmentType.BAN || type == PunishmentType.MUTE) {
                duration = "Permanent";
            }
            return Optional.of(realtimeEntry(type, playerName, actionMatcher.group(1),
                    cleanReason(reason), duration));
        }

        Matcher kickMatcher = KICK_ACTION.matcher(clean);
        if (kickMatcher.matches()) {
            String playerName = kickMatcher.group(1);
            return Optional.of(realtimeEntry(PunishmentType.KICK, playerName, kickMatcher.group(2),
                    cleanReason(kickMatcher.group(3)), ""));
        }

        Matcher warningMatcher = PASSIVE_WARNING.matcher(clean);
        if (warningMatcher.matches()) {
            String playerName = warningMatcher.group(1);
            return Optional.of(realtimeEntry(PunishmentType.WARNING, playerName, warningMatcher.group(2),
                    cleanReason(warningMatcher.group(3)), ""));
        }
        return Optional.empty();
    }

    static List<PunishmentLogEntry> parsePage(PunishmentType type, String html) {
        if (type == null || html == null || html.isBlank()) return List.of();
        Matcher bodyMatcher = TABLE_BODY.matcher(html);
        if (!bodyMatcher.find()) return List.of();

        List<PunishmentLogEntry> entries = new ArrayList<>();
        long loadedAt = System.currentTimeMillis();
        Matcher rowMatcher = TABLE_ROW.matcher(bodyMatcher.group(1));
        while (rowMatcher.find() && entries.size() < MAX_ENTRIES) {
            List<String> cells = new ArrayList<>();
            List<String> rawCells = new ArrayList<>();
            Matcher cellMatcher = TABLE_CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                rawCells.add(cellMatcher.group(1));
                cells.add(htmlText(cellMatcher.group(1)));
            }
            if (cells.size() < 2 || cells.get(0).isBlank() || cells.get(1).isBlank()) continue;

            String playerName = cells.get(0);
            String staffName = cells.get(1);
            UUID playerUuid = uuidFromPlayerCell(rawCells.get(0)).orElseGet(() -> offlineUuid(playerName));
            String reason = cells.size() > 2 ? cells.get(2) : "";
            String occurredAt = "";
            String expiresAt = "";
            if (type == PunishmentType.BAN || type == PunishmentType.MUTE) {
                occurredAt = cells.size() > 3 ? cells.get(3) : "";
                expiresAt = cells.size() > 4 ? cells.get(4) : "";
            } else if (type == PunishmentType.WARNING) {
                expiresAt = cells.size() > 3 ? cells.get(3) : "";
            } else if (type == PunishmentType.KICK) {
                occurredAt = cells.size() > 3 ? cells.get(3) : "";
            }
            entries.add(new PunishmentLogEntry(type, playerName, playerUuid, staffName,
                    reason, websiteDuration(type, occurredAt, expiresAt), occurredAt, expiresAt,
                    PunishmentLogOrigin.WEBSITE, detailsUrlFromCell(rawCells.get(0)),
                    avatarUrlFromCell(rawCells.get(0)), loadedAt));
        }
        return List.copyOf(entries);
    }

    static List<PunishmentLogEntry> preserveWebsiteHighlightTimes(List<PunishmentLogEntry> previous,
                                                                  List<PunishmentLogEntry> refreshed) {
        List<PunishmentLogEntry> preserved = new ArrayList<>(refreshed.size());
        for (PunishmentLogEntry entry : refreshed) {
            PunishmentLogEntry existing = previous.stream()
                    .filter(candidate -> sameWebsiteEntry(candidate, entry))
                    .findFirst().orElse(null);
            preserved.add(existing == null ? entry
                    : entry.withHighlightFadeStartedAt(existing.highlightFadeStartedAtMillis()));
        }
        return List.copyOf(preserved);
    }

    private static boolean sameWebsiteEntry(PunishmentLogEntry first, PunishmentLogEntry second) {
        if (!first.detailsUrl().isEmpty() && !second.detailsUrl().isEmpty()) {
            return first.detailsUrl().equals(second.detailsUrl());
        }
        return first.type() == second.type()
                && first.playerName().equalsIgnoreCase(second.playerName())
                && first.staffName().equalsIgnoreCase(second.staffName())
                && first.occurredAt().equals(second.occurredAt());
    }

    static List<PunishmentLogEntry> mergeEntries(List<PunishmentLogEntry> local,
                                                 List<PunishmentLogEntry> website) {
        List<PunishmentLogEntry> merged = new ArrayList<>(local.size() + website.size());
        merged.addAll(local);
        boolean[] matchedLocal = new boolean[local.size()];
        for (PunishmentLogEntry entry : website) {
            int duplicateIndex = -1;
            for (int index = 0; index < local.size(); index++) {
                if (!matchedLocal[index] && sameOccurrence(local.get(index), entry)) {
                    duplicateIndex = index;
                    break;
                }
            }
            if (duplicateIndex >= 0) matchedLocal[duplicateIndex] = true;
            else merged.add(entry);
        }
        merged.sort(Comparator.comparingLong(PunishmentLogService::punishmentTimestamp).reversed());
        return List.copyOf(merged.subList(0, Math.min(MAX_ENTRIES, merged.size())));
    }

    private static boolean sameOccurrence(PunishmentLogEntry local, PunishmentLogEntry website) {
        if (!EntryKey.of(local).equals(EntryKey.of(website))) return false;
        long localTimestamp = punishmentTimestamp(local);
        long websiteTimestamp = punishmentTimestamp(website);
        return localTimestamp != Long.MIN_VALUE && websiteTimestamp != Long.MIN_VALUE
                && Math.abs(localTimestamp - websiteTimestamp) <= WEBSITE_DUPLICATE_TOLERANCE_MILLIS;
    }

    private static long punishmentTimestamp(PunishmentLogEntry entry) {
        String occurredAt = entry.occurredAt();
        if (occurredAt.isEmpty()) return Long.MIN_VALUE;
        for (DateTimeFormatter formatter : List.of(LOG_TIME, WEBSITE_TIME)) {
            try {
                return LocalDateTime.parse(withoutAnnotation(occurredAt), formatter)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // Try the next known timestamp format.
            }
        }
        return Long.MIN_VALUE;
    }

    private static PunishmentLogEntry realtimeEntry(PunishmentType type, String playerName,
                                                    String staffName, String reason, String duration) {
        long now = System.currentTimeMillis();
        return new PunishmentLogEntry(type, playerName, offlineUuid(playerName), staffName,
                reason, duration, currentLogTime(), "", PunishmentLogOrigin.REALTIME,
                "", "", now);
    }

    private static String currentLogTime() {
        return LocalDateTime.now().format(LOG_TIME);
    }

    private static String displayCommandDuration(String duration) {
        return duration.equalsIgnoreCase("perm") || duration.equalsIgnoreCase("permanent")
                ? "Permanent" : duration;
    }

    private static String cleanReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.endsWith(".") && (reason.length() < 2
                || reason.charAt(reason.length() - 2) == '\'' || reason.charAt(reason.length() - 2) == '"')) {
            reason = reason.substring(0, reason.length() - 1).trim();
        }
        if (reason.length() >= 2) {
            char first = reason.charAt(0);
            char last = reason.charAt(reason.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                reason = reason.substring(1, reason.length() - 1);
            }
        }
        return reason.trim();
    }

    private static String websiteDuration(PunishmentType type, String occurredAt, String expiresAt) {
        if (type != PunishmentType.BAN && type != PunishmentType.MUTE) return "";
        if (expiresAt.toLowerCase(Locale.ROOT).startsWith("permanent")) return "Permanent";
        try {
            LocalDateTime start = LocalDateTime.parse(withoutAnnotation(occurredAt), WEBSITE_TIME);
            LocalDateTime end = LocalDateTime.parse(withoutAnnotation(expiresAt), WEBSITE_TIME);
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes <= 0L) return "";
            if (minutes % (60L * 24L) == 0L) {
                long days = minutes / (60L * 24L);
                return days + (days == 1L ? " day" : " days");
            }
            if (minutes % 60L == 0L) {
                long hours = minutes / 60L;
                return hours + (hours == 1L ? " hour" : " hours");
            }
            return minutes + (minutes == 1L ? " minute" : " minutes");
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private static String withoutAnnotation(String value) {
        int annotation = value.indexOf(" (");
        return (annotation < 0 ? value : value.substring(0, annotation)).trim();
    }

    private static String avatarUrlFromCell(String cell) {
        Matcher matcher = AVATAR_URL.matcher(cell);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String detailsUrlFromCell(String cell) {
        Matcher matcher = DETAILS_URL.matcher(cell);
        if (!matcher.find()) return "";
        String link = matcher.group(1).replace("&amp;", "&");
        try {
            return URI.create(BASE_URL).resolve(link).toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static java.util.Optional<UUID> uuidFromPlayerCell(String cell) {
        Matcher matcher = PLAYER_UUID.matcher(cell);
        if (!matcher.find()) return java.util.Optional.empty();
        try {
            String raw = matcher.group(1);
            return java.util.Optional.of(UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12)
                    + "-" + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    private static String htmlText(String html) {
        String withoutTags = HTML_TAG.matcher(html).replaceAll(" ");
        String decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }

    private record Page(PunishmentType type, String path) {
    }

    private record PageResult(PunishmentType type, List<PunishmentLogEntry> entries, boolean success) {
        private static PageResult success(PunishmentType type, List<PunishmentLogEntry> entries) {
            return new PageResult(type, List.copyOf(entries), true);
        }

        private static PageResult failed(PunishmentType type) {
            return new PageResult(type, List.of(), false);
        }
    }

    private record LocalEntry(PunishmentLogEntry entry, long createdAtMillis, LocalSource source) {
    }

    private enum LocalSource { COMMAND, CHAT }

    private record EntryKey(PunishmentType type, String playerName, String staffName) {
        private static EntryKey of(PunishmentLogEntry entry) {
            return new EntryKey(entry.type(), entry.playerName().toLowerCase(Locale.ROOT),
                    entry.staffName().toLowerCase(Locale.ROOT));
        }
    }

    static synchronized void resetForTests() {
        INSTANCE.resetSession();
    }

    static synchronized List<PunishmentLogEntry> localEntriesForTests() {
        return INSTANCE.localEntries.stream().map(LocalEntry::entry).toList();
    }
}
