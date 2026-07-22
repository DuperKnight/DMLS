package com.duperknight.client.accountlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscordAccountProfileStoreTest {
    @Test
    void storesLoadsAndDeletesProfile(@TempDir Path temporaryDirectory) {
        Path file = temporaryDirectory.resolve("nested/profiles.json");
        UUID minecraftUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        DiscordAccountProfile profile = new DiscordAccountProfile(
                minecraftUuid,
                "123456789012345678",
                "username",
                "Server nickname",
                "https://cdn.discordapp.com/avatars/123/avatar.png");

        assertTrue(DiscordAccountProfileStore.saveTo(file, profile));
        assertEquals(profile, DiscordAccountProfileStore.loadFrom(file, minecraftUuid).orElseThrow());
        assertTrue(DiscordAccountProfileStore.deleteFrom(file, minecraftUuid));
        assertFalse(DiscordAccountProfileStore.loadFrom(file, minecraftUuid).isPresent());
    }
}
