package com.duperknight.client.modules;

import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatReplayModuleTest {
    @TempDir
    Path directory;

    @AfterEach
    void reset() {
        ChatReplayModule.resetForTests();
    }

    @Test
    void retainsOnlyTenNewestMinecraftLaunchSessions() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 7, 13, 10, 0);
        for (int index = 0; index < 11; index++) {
            ChatReplayModule.resetForTests();
            ChatReplayModule.initializeStorage(directory, start.plusMinutes(index));
            String value = "session " + index;
            ChatReplayModule.capture(new ServerMessage(Text.literal(value), value,
                    MessageOrigin.SERVER_SYSTEM, false, index));
        }

        assertEquals(10, ChatReplayModule.sessions().size());
        assertEquals("2026-07-13 10:10:00", ChatReplayModule.sessions().getFirst().startedAt());
        assertEquals("2026-07-13 10:01:00", ChatReplayModule.sessions().getLast().startedAt());
        try (var files = Files.list(directory)) {
            assertEquals(10, files.count());
        }
    }

    @Test
    void doesNotCreateAFileOrEnableExportForAnEmptySession() throws Exception {
        ChatReplayModule.initializeStorage(directory, LocalDateTime.of(2026, 7, 13, 19, 0));

        assertFalse(ChatReplayModule.isSessionStored(ChatReplayModule.currentSessionId()));
        ChatReplayModule.Chunk empty = ChatReplayModule.loadChunk(ChatReplayModule.currentSessionId(), "", -1).join();
        assertTrue(empty.entries().isEmpty());
        assertFalse(empty.hasPrevious());
        assertFalse(empty.hasNext());
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void capturesOnlyTheTwoOriginsSubscribedByTheMessageRouter() {
        ChatReplayModule.initializeStorage(directory, LocalDateTime.of(2026, 7, 13, 19, 30));

        ChatReplayModule.capture(message("overlay", MessageOrigin.OVERLAY));
        ChatReplayModule.capture(message("local", MessageOrigin.DMLS_LOCAL));
        ChatReplayModule.capture(message("", MessageOrigin.SERVER_SYSTEM));
        assertEquals(0, ChatReplayModule.currentMessageCount());

        ChatReplayModule.capture(message("player", MessageOrigin.PLAYER_CHAT));
        ChatReplayModule.capture(message("system", MessageOrigin.SERVER_SYSTEM));

        assertEquals(2, ChatReplayModule.currentMessageCount());
        ChatReplayModule.Chunk chunk = ChatReplayModule.loadChunk(
                ChatReplayModule.currentSessionId(), "", -1).join();
        assertEquals(List.of("player", "system"),
                chunk.entries().stream().map(ChatReplayModule.Entry::cleanText).toList());
    }

    @Test
    void loadsCompleteHistoryInBoundedChunksAndSearchesTheWholeFile() throws Exception {
        ChatReplayModule.initializeStorage(directory, LocalDateTime.of(2026, 7, 13, 20, 1, 24));
        int messageCount = ChatReplayModule.DISPLAY_CHUNK_SIZE + 5;
        for (int index = 0; index < messageCount; index++) {
            String value = "match " + index;
            ChatReplayModule.capture(new ServerMessage(Text.literal(value), value,
                    MessageOrigin.PLAYER_CHAT, false, index));
        }

        String sessionId = ChatReplayModule.currentSessionId();
        ChatReplayModule.Chunk first = ChatReplayModule.loadChunk(sessionId, "", 0).join();
        ChatReplayModule.Chunk second = ChatReplayModule.loadChunk(sessionId, "", ChatReplayModule.DISPLAY_CHUNK_SIZE).join();
        ChatReplayModule.Chunk last = ChatReplayModule.loadChunk(sessionId, "", -1).join();
        ChatReplayModule.Chunk searchFirst = ChatReplayModule.loadChunk(sessionId, "match", 0).join();
        ChatReplayModule.Chunk searchLast = ChatReplayModule.loadChunk(sessionId, "match", ChatReplayModule.DISPLAY_CHUNK_SIZE).join();
        ChatReplayModule.Chunk previousFromTail = ChatReplayModule.loadChunkEndingAt(sessionId, "", 5).join();

        assertEquals(ChatReplayModule.DISPLAY_CHUNK_SIZE, first.entries().size());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        assertEquals(5, second.entries().size());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
        assertEquals(ChatReplayModule.DISPLAY_CHUNK_SIZE, last.entries().size());
        assertEquals("match 5", last.entries().getFirst().cleanText());
        assertFalse(last.hasNext());
        assertEquals(ChatReplayModule.DISPLAY_CHUNK_SIZE, searchFirst.entries().size());
        assertTrue(searchFirst.hasNext());
        assertEquals(5, searchLast.entries().size());
        assertFalse(searchLast.hasNext());
        assertEquals(6, previousFromTail.entries().size());
        assertEquals("match 0", previousFromTail.entries().getFirst().cleanText());
        assertEquals("match 5", previousFromTail.entries().getLast().cleanText());
        assertFalse(previousFromTail.hasPrevious());
        assertTrue(previousFromTail.hasNext());

        ChatReplayModule.CurrentUpdates updates = ChatReplayModule.currentUpdatesAfter(sessionId, messageCount - 2);
        assertTrue(updates.complete());
        assertEquals(List.of("match 1003", "match 1004"),
                updates.entries().stream().map(ChatReplayModule.Entry::cleanText).toList());

        Path export = ChatReplayModule.export(sessionId, "match 1004").join();
        assertEquals(List.of("[" + searchLast.entries().getLast().time() + "] match 1004"), Files.readAllLines(export));
    }

    private static ServerMessage message(String value, MessageOrigin origin) {
        return new ServerMessage(Text.literal(value), value, origin,
                origin == MessageOrigin.OVERLAY, System.nanoTime());
    }
}
