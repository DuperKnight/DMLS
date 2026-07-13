package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.modules.ChatReplayScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Stores the last ten Minecraft-launch chat sessions as append-only files. */
public final class ChatReplayModule extends DMLSModule {
    public static final int DISPLAY_CHUNK_SIZE = 1000;
    private static final int MAX_SESSIONS = 10;
    private static final DateTimeFormatter SESSION_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter ENTRY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Gson GSON = new Gson();
    private static final EnumSet<MessageOrigin> CAPTURED_ORIGINS =
            EnumSet.of(MessageOrigin.PLAYER_CHAT, MessageOrigin.SERVER_SYSTEM);
    private static final Deque<StoredSession> SESSIONS = new ArrayDeque<>();
    private static final Deque<Entry> CURRENT_TAIL = new ArrayDeque<>(DISPLAY_CHUNK_SIZE);
    private static final AtomicLong LOAD_GENERATION = new AtomicLong();

    private static Path storageDirectory;
    private static StoredSession currentSession;
    private static BufferedWriter currentWriter;
    private static boolean initialized;
    private static long revision;
    private static int currentMessageCount;

    /** One captured chat line with its arrival time. */
    public record Entry(String time, Text text, String cleanText) {
    }

    /** Lightweight session metadata; message contents remain on disk until a chunk is requested. */
    public record Session(String id, String startedAt) {
    }

    /** A bounded page of matches from the complete session file. */
    public record Chunk(List<Entry> entries, int offset, boolean hasPrevious, boolean hasNext) {
    }

    /** Messages appended after a current-session message index. */
    public record CurrentUpdates(List<Entry> entries, int messageCount, boolean complete) {
    }

    public ChatReplayModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.chat_replay.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.chat_replay.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ChatReplayScreen(parent, this, ""));
    }

    /** Opens the replay screen with a prefilled filter. */
    public void openScreenWithFilter(MinecraftClient client, String filter) {
        client.send(() -> client.setScreen(new ChatReplayScreen(null, this, filter)));
    }

    @Override
    public void register() {
        initializeStorage(defaultDirectory(), LocalDateTime.now());
        ServerMessageRouter.subscribe(EnumSet.copyOf(CAPTURED_ORIGINS), ChatReplayModule::capture);
    }

    static synchronized void capture(ServerMessage message) {
        if (!acceptsForReplay(message)) {
            return;
        }
        ensureInitialized();

        Entry entry = new Entry(LocalDateTime.now().format(ENTRY_TIME_FORMAT), message.text(), message.cleanText());
        try {
            ensureCurrentSessionFile();
            currentWriter.write(encodeEntry(entry));
            currentWriter.newLine();
            currentWriter.flush();
            if (CURRENT_TAIL.size() == DISPLAY_CHUNK_SIZE) CURRENT_TAIL.removeFirst();
            CURRENT_TAIL.addLast(entry);
            currentMessageCount++;
            revision++;
            pruneOldSessionsSafely();
        } catch (IOException | RuntimeException e) {
            DMLS.LOGGER.error("Failed to append chat replay entry to {}", currentSession.path, e);
        }
    }

    /** Mirrors the router subscription so direct captures cannot persist overlay or local DMLS output. */
    static boolean acceptsForReplay(ServerMessage message) {
        return message != null
                && CAPTURED_ORIGINS.contains(message.origin())
                && message.cleanText() != null
                && !message.cleanText().isEmpty();
    }

    public static synchronized List<Session> sessions() {
        ensureInitialized();
        List<Session> snapshots = new ArrayList<>();
        var newestFirst = SESSIONS.descendingIterator();
        while (newestFirst.hasNext() && snapshots.size() < MAX_SESSIONS) {
            snapshots.add(newestFirst.next().metadata());
        }
        return List.copyOf(snapshots);
    }

    public static synchronized String currentSessionId() {
        ensureInitialized();
        return currentSession.id;
    }

    /** Loads a chunk asynchronously. Offset -1 requests the final chunk of all matching messages. */
    public static CompletableFuture<Chunk> loadChunk(String sessionId, String filter, int offset) {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        if (offset < 0 && needle.isEmpty()) {
            Optional<Chunk> currentTail = currentTailChunk(sessionId);
            if (currentTail.isPresent()) {
                LOAD_GENERATION.incrementAndGet();
                return CompletableFuture.completedFuture(currentTail.get());
            }
        }
        return loadChunkFromFile(sessionId, needle, offset, DISPLAY_CHUNK_SIZE);
    }

    /** Loads the chunk whose final entry is the supplied match offset. */
    public static CompletableFuture<Chunk> loadChunkEndingAt(String sessionId, String filter, int endOffset) {
        int startOffset = Math.max(0, endOffset - DISPLAY_CHUNK_SIZE + 1);
        int limit = Math.max(1, endOffset - startOffset + 1);
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        return loadChunkFromFile(sessionId, needle, startOffset, limit);
    }

    private static CompletableFuture<Chunk> loadChunkFromFile(String sessionId, String needle,
                                                               int offset, int limit) {
        Optional<Path> path = sessionPath(sessionId).filter(Files::exists);
        if (path.isEmpty()) {
            return CompletableFuture.completedFuture(new Chunk(List.of(), 0, false, false));
        }
        long generation = LOAD_GENERATION.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> readChunk(path.get(), needle, offset, limit, generation));
    }

    /** Exports every matching entry as readable text without retaining the full result in memory. */
    public static CompletableFuture<Path> export(String sessionId, String filter) {
        Optional<Path> path = sessionPath(sessionId).filter(Files::exists);
        if (path.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("This session has no saved messages"));
        }
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> exportSession(path.get(), sessionId, needle));
    }

    public static synchronized boolean isSessionStored(String sessionId) {
        return SESSIONS.stream().anyMatch(session -> session.id.equals(sessionId) && session.persisted);
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized int currentMessageCount() {
        return currentMessageCount;
    }

    public static synchronized CurrentUpdates currentUpdatesAfter(String sessionId, int afterMessageCount) {
        if (currentSession == null || !currentSession.id.equals(sessionId)) {
            return new CurrentUpdates(List.of(), currentMessageCount, false);
        }
        int firstRetainedIndex = currentMessageCount - CURRENT_TAIL.size();
        if (afterMessageCount < firstRetainedIndex || afterMessageCount > currentMessageCount) {
            return new CurrentUpdates(List.of(), currentMessageCount, false);
        }
        int skip = afterMessageCount - firstRetainedIndex;
        return new CurrentUpdates(CURRENT_TAIL.stream().skip(skip).toList(), currentMessageCount, true);
    }

    private static synchronized Optional<Path> sessionPath(String id) {
        return SESSIONS.stream().filter(session -> session.id.equals(id)).map(session -> session.path).findFirst();
    }

    private static synchronized Optional<Chunk> currentTailChunk(String sessionId) {
        if (currentSession == null || !currentSession.id.equals(sessionId)) return Optional.empty();
        int offset = Math.max(0, currentMessageCount - CURRENT_TAIL.size());
        return Optional.of(new Chunk(List.copyOf(CURRENT_TAIL), offset, offset > 0, false));
    }

    private static Chunk readChunk(Path path, String needle, int requestedOffset, int limit, long generation) {
        if (requestedOffset < 0) {
            return readFinalChunk(path, needle, limit, generation);
        }

        List<Entry> entries = new ArrayList<>(limit);
        int matchIndex = 0;
        boolean hasNext = false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.readLine(); // Session metadata header.
            String line;
            while ((line = reader.readLine()) != null) {
                throwIfLoadCancelled(generation);
                Optional<Entry> decoded = decodeEntry(line);
                if (decoded.isEmpty() || !matches(decoded.get(), needle)) continue;
                if (matchIndex >= requestedOffset && entries.size() < limit) {
                    entries.add(decoded.get());
                } else if (matchIndex >= requestedOffset + limit) {
                    hasNext = true;
                    break;
                }
                matchIndex++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat replay " + path, e);
        }

        return new Chunk(List.copyOf(entries), requestedOffset, requestedOffset > 0, hasNext);
    }

    private static Chunk readFinalChunk(Path path, String needle, int limit, long generation) {
        Deque<Entry> tail = new ArrayDeque<>(limit);
        int matchCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                throwIfLoadCancelled(generation);
                Optional<Entry> decoded = decodeEntry(line);
                if (decoded.isEmpty() || !matches(decoded.get(), needle)) continue;
                if (tail.size() == limit) tail.removeFirst();
                tail.addLast(decoded.get());
                matchCount++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat replay " + path, e);
        }
        int offset = Math.max(0, matchCount - tail.size());
        return new Chunk(List.copyOf(tail), offset, offset > 0, false);
    }

    private static void throwIfLoadCancelled(long generation) {
        if (generation != LOAD_GENERATION.get()) {
            throw new CancellationException("Superseded chat replay load");
        }
    }

    private static boolean matches(Entry entry, String needle) {
        return needle.isEmpty() || entry.cleanText().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Path exportSession(Path source, String sessionId, String needle) {
        try {
            Path exportDirectory = storageDirectory.resolve("exports");
            Files.createDirectories(exportDirectory);
            String filterSuffix = needle.isEmpty() ? "" : "-search";
            Path destination = uniqueExportPath(exportDirectory,
                    "chat-replay-" + sessionId + filterSuffix + ".txt");
            try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
                 BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE_NEW)) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    Optional<Entry> decoded = decodeEntry(line);
                    if (decoded.isEmpty() || !matches(decoded.get(), needle)) continue;
                    writer.write("[" + decoded.get().time() + "] " + decoded.get().cleanText());
                    writer.newLine();
                }
            }
            return destination;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export chat replay", e);
        }
    }

    private static Path uniqueExportPath(Path directory, String filename) {
        Path candidate = directory.resolve(filename);
        int extension = filename.lastIndexOf('.');
        String stem = extension < 0 ? filename : filename.substring(0, extension);
        String suffix = extension < 0 ? "" : filename.substring(extension);
        int number = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(stem + "-" + number++ + suffix);
        }
        return candidate;
    }

    private static String encodeEntry(Entry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("time", entry.time());
        object.addProperty("cleanText", entry.cleanText());
        JsonArray segments = new JsonArray();
        entry.text().visit((style, value) -> {
            JsonObject segment = new JsonObject();
            segment.addProperty("value", value);
            if (style.getColor() != null) segment.addProperty("color", style.getColor().getRgb());
            if (style.isBold()) segment.addProperty("bold", true);
            if (style.isItalic()) segment.addProperty("italic", true);
            if (style.isUnderlined()) segment.addProperty("underlined", true);
            if (style.isStrikethrough()) segment.addProperty("strikethrough", true);
            if (style.isObfuscated()) segment.addProperty("obfuscated", true);
            segments.add(segment);
            return Optional.empty();
        }, Style.EMPTY);
        object.add("segments", segments);
        return GSON.toJson(object);
    }

    private static Optional<Entry> decodeEntry(String line) {
        try {
            JsonObject object = JsonParser.parseString(line).getAsJsonObject();
            String cleanText = object.get("cleanText").getAsString();
            MutableText text = Text.empty();
            for (var element : object.getAsJsonArray("segments")) {
                JsonObject segment = element.getAsJsonObject();
                Style style = Style.EMPTY;
                if (segment.has("color")) style = style.withColor(segment.get("color").getAsInt());
                if (segment.has("bold")) style = style.withBold(true);
                if (segment.has("italic")) style = style.withItalic(true);
                if (segment.has("underlined")) style = style.withUnderline(true);
                if (segment.has("strikethrough")) style = style.withStrikethrough(true);
                if (segment.has("obfuscated")) style = style.withObfuscated(true);
                text.append(Text.literal(segment.get("value").getAsString()).setStyle(style));
            }
            return Optional.of(new Entry(object.get("time").getAsString(), text, cleanText));
        } catch (RuntimeException e) {
            DMLS.LOGGER.warn("Skipping malformed chat replay line", e);
            return Optional.empty();
        }
    }

    private static synchronized void ensureInitialized() {
        if (!initialized) {
            initializeStorage(storageDirectory == null ? defaultDirectory() : storageDirectory, LocalDateTime.now());
        }
    }

    private static Path defaultDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls-chat-replay");
    }

    static synchronized void initializeStorage(Path directory, LocalDateTime startedAt) {
        if (initialized) {
            return;
        }
        initialized = true;
        storageDirectory = directory;
        SESSIONS.clear();

        try {
            Files.createDirectories(storageDirectory);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(storageDirectory, "session-*.jsonl")) {
                for (Path path : files) {
                    readSession(path).ifPresent(SESSIONS::addLast);
                }
            }
            List<StoredSession> ordered = SESSIONS.stream()
                    .sorted(Comparator.comparing(session -> session.id))
                    .toList();
            SESSIONS.clear();
            SESSIONS.addAll(ordered);

            String id = FILE_TIME_FORMAT.format(startedAt);
            Path path = uniqueSessionPath(id);
            id = path.getFileName().toString().substring("session-".length(), path.getFileName().toString().length() - ".jsonl".length());
            currentSession = new StoredSession(id, SESSION_LABEL_FORMAT.format(startedAt), path, false);
            SESSIONS.addLast(currentSession);
            revision++;
        } catch (IOException e) {
            initialized = false;
            throw new IllegalStateException("Failed to initialize chat replay storage", e);
        }
    }

    private static Optional<StoredSession> readSession(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return Optional.empty();
            JsonObject header = JsonParser.parseString(headerLine).getAsJsonObject();
            if (reader.readLine() == null) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            String filename = path.getFileName().toString();
            String id = filename.substring("session-".length(), filename.length() - ".jsonl".length());
            return Optional.of(new StoredSession(id, header.get("startedAt").getAsString(), path, true));
        } catch (IOException | RuntimeException e) {
            DMLS.LOGGER.warn("Ignoring unreadable chat replay session {}", path, e);
            return Optional.empty();
        }
    }

    private static Path uniqueSessionPath(String baseId) {
        Path path = storageDirectory.resolve("session-" + baseId + ".jsonl");
        int suffix = 1;
        while (Files.exists(path)) {
            path = storageDirectory.resolve("session-" + baseId + "-" + suffix++ + ".jsonl");
        }
        return path;
    }

    private static void pruneOldSessionsSafely() {
        while (SESSIONS.stream().filter(session -> session.persisted).count() > MAX_SESSIONS) {
            StoredSession oldest = SESSIONS.getFirst();
            try {
                if (oldest.persisted) Files.deleteIfExists(oldest.path);
                SESSIONS.removeFirst();
            } catch (IOException e) {
                DMLS.LOGGER.warn("Could not prune old chat replay session {}", oldest.path, e);
                return;
            }
        }
    }

    private static void ensureCurrentSessionFile() throws IOException {
        if (currentSession.persisted) return;
        JsonObject header = new JsonObject();
        header.addProperty("startedAt", currentSession.startedAt);
        Files.writeString(currentSession.path, GSON.toJson(header) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        currentWriter = Files.newBufferedWriter(currentSession.path, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        currentSession.persisted = true;
    }

    static synchronized void resetForTests() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException ignored) {
            }
        }
        SESSIONS.clear();
        currentSession = null;
        currentWriter = null;
        CURRENT_TAIL.clear();
        currentMessageCount = 0;
        LOAD_GENERATION.incrementAndGet();
        storageDirectory = null;
        initialized = false;
        revision = 0;
    }

    private static final class StoredSession {
        private final String id;
        private final String startedAt;
        private final Path path;
        private boolean persisted;

        private StoredSession(String id, String startedAt, Path path, boolean persisted) {
            this.id = id;
            this.startedAt = startedAt;
            this.path = path;
            this.persisted = persisted;
        }

        private Session metadata() {
            return new Session(id, startedAt);
        }
    }
}
