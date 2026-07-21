package com.duperknight.client.accountlink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscordLinkTokenStoreTest {
    private static final String FIRST_TOKEN = "dmls_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
    private static final String SECOND_TOKEN = "dmls_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

    @Test
    void atomicallyPreservesTokensForMultipleAccounts(@TempDir Path temporaryDirectory) throws Exception {
        Path tokenFile = temporaryDirectory.resolve("nested/dmls-account-links.json");
        UUID firstUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID secondUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertTrue(DiscordLinkTokenStore.saveTo(tokenFile, firstUuid, FIRST_TOKEN));
        assertTrue(DiscordLinkTokenStore.saveTo(tokenFile, secondUuid, SECOND_TOKEN));

        JsonObject root = JsonParser.parseString(Files.readString(tokenFile)).getAsJsonObject();
        JsonObject tokens = root.getAsJsonObject("tokens");
        assertEquals(1, root.get("version").getAsInt());
        assertEquals(FIRST_TOKEN, tokens.get(firstUuid.toString()).getAsString());
        assertEquals(SECOND_TOKEN, tokens.get(secondUuid.toString()).getAsString());
        assertEquals(FIRST_TOKEN, DiscordLinkTokenStore.loadFrom(tokenFile, firstUuid).orElseThrow());

        assertTrue(DiscordLinkTokenStore.deleteFrom(tokenFile, firstUuid));
        assertFalse(DiscordLinkTokenStore.loadFrom(tokenFile, firstUuid).isPresent());
        assertEquals(SECOND_TOKEN, DiscordLinkTokenStore.loadFrom(tokenFile, secondUuid).orElseThrow());

        try {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(tokenFile));
        } catch (UnsupportedOperationException ignored) {
            // Permission semantics are platform-specific outside POSIX file systems.
        }
    }
}
