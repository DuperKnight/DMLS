package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static com.duperknight.client.modules.LocationsModule.Outcome.DELETED;
import static com.duperknight.client.modules.LocationsModule.Outcome.IO_ERROR;
import static com.duperknight.client.modules.LocationsModule.Outcome.SAVED;
import static com.duperknight.client.modules.LocationsModule.Outcome.UPDATED;
import static com.duperknight.client.modules.LocationsModule.TeleportCompatibility.READY;
import static com.duperknight.client.modules.LocationsModule.TeleportCompatibility.UNBOUND;
import static com.duperknight.client.modules.LocationsModule.TeleportCompatibility.WRONG_SERVER;
import static com.duperknight.client.modules.LocationsModule.TeleportCompatibility.WRONG_WORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationsModuleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyRowsOnlyWhenThereIsExactlyOneExactAllowedHost() throws Exception {
        Path boundPath = writeProperties("bound.properties", "site", "minecraft:overworld;10;64;-3");
        LocationsModule bound = new LocationsModule(boundPath,
                () -> List.of("*.stoneworks.gg", "PLAY.STONEWORKS.GG."));
        bound.register();

        assertEquals("play.stoneworks.gg", bound.entries().get("site").server());
        LocationsModule boundReloaded = new LocationsModule(boundPath,
                () -> List.of("other.example", "second.example"));
        boundReloaded.register();
        assertEquals("play.stoneworks.gg", boundReloaded.entries().get("site").server(),
                "a migrated binding must not change with the later allowlist");

        Path ambiguousPath = writeProperties("ambiguous.properties", "site", "minecraft:overworld;10;64;-3");
        LocationsModule ambiguous = new LocationsModule(ambiguousPath,
                () -> List.of("play.stoneworks.gg", "test.stoneworks.gg"));
        ambiguous.register();
        assertFalse(ambiguous.entries().get("site").isBound());
        LocationsModule ambiguousReloaded = new LocationsModule(ambiguousPath,
                () -> List.of("play.stoneworks.gg"));
        ambiguousReloaded.register();
        assertFalse(ambiguousReloaded.entries().get("site").isBound(),
                "an ambiguous legacy row must stay unbound until the user re-saves it");

        Path wildcardPath = writeProperties("wildcard.properties", "site", "minecraft:overworld;10;64;-3");
        LocationsModule wildcardOnly = new LocationsModule(wildcardPath, () -> List.of("*.stoneworks.gg"));
        wildcardOnly.register();
        assertFalse(wildcardOnly.entries().get("site").isBound());
    }

    @Test
    void loadsV2RowsAndIsolatesCorruptRows() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("valid", "play.stoneworks.gg;minecraft:the_nether;-5;70;9");
        properties.setProperty("bad-number", "play.stoneworks.gg;minecraft:overworld;nope;70;9");
        properties.setProperty("bad-server", "https://example.org;minecraft:overworld;1;2;3");
        properties.setProperty("bad-shape", "minecraft:overworld;1;2");
        Path path = temporaryDirectory.resolve("locations.properties");
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "test");
        }

        LocationsModule module = new LocationsModule(path, () -> List.of("play.stoneworks.gg"));
        module.register();

        assertEquals(List.of("valid"), module.names());
        assertEquals(new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:the_nether", -5, 70, 9), module.entries().get("valid"));
    }

    @Test
    void successfulMutationWritesV2AndCanBeReloaded() {
        Path path = temporaryDirectory.resolve("locations.properties");
        LocationsModule module = new LocationsModule(path, () -> List.of("play.stoneworks.gg"));
        LocationsModule.SavedLocation first = new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:overworld", 1, 2, 3);
        LocationsModule.SavedLocation second = new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:overworld", 4, 5, 6);

        assertEquals(SAVED, module.storeLocation("Site", first));
        assertEquals(UPDATED, module.storeLocation("Site", second));

        LocationsModule reloaded = new LocationsModule(path, () -> List.of("unused.example"));
        reloaded.register();
        assertEquals(second, reloaded.entries().get("Site"));
        assertEquals(DELETED, reloaded.deleteLocation("Site"));
        assertTrue(reloaded.entries().isEmpty());
    }

    @Test
    void persistenceFailureDoesNotCommitCandidateMap() throws Exception {
        Path path = temporaryDirectory.resolve("locations.properties");
        LocationsModule module = new LocationsModule(path, () -> List.of("play.stoneworks.gg"));
        LocationsModule.SavedLocation original = new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:overworld", 1, 2, 3);
        LocationsModule.SavedLocation candidate = new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:overworld", 9, 9, 9);
        assertEquals(SAVED, module.storeLocation("Site", original));

        Files.delete(path);
        Files.createDirectory(path);

        assertEquals(IO_ERROR, module.storeLocation("Site", candidate));
        assertEquals(original, module.entries().get("Site"));
        assertEquals(IO_ERROR, module.deleteLocation("Site"));
        assertEquals(original, module.entries().get("Site"));
    }

    @Test
    void teleportRequiresABoundMatchingServerAndDimension() {
        LocationsModule.SavedLocation location = new LocationsModule.SavedLocation(
                "play.stoneworks.gg", "minecraft:overworld", 1, 2, 3);
        assertEquals(READY, LocationsModule.compatibility(location,
                "PLAY.STONEWORKS.GG.", "minecraft:overworld"));
        assertEquals(READY, LocationsModule.compatibility(location,
                "play.stoneworks.gg:25565", "minecraft:overworld"));
        assertEquals(WRONG_SERVER, LocationsModule.compatibility(location,
                "test.stoneworks.gg", "minecraft:overworld"));
        assertEquals(WRONG_WORLD, LocationsModule.compatibility(location,
                "play.stoneworks.gg", "minecraft:the_nether"));
        assertEquals(UNBOUND, LocationsModule.compatibility(
                new LocationsModule.SavedLocation("", "minecraft:overworld", 1, 2, 3),
                "play.stoneworks.gg", "minecraft:overworld"));
    }

    private Path writeProperties(String fileName, String key, String value) throws Exception {
        Path path = temporaryDirectory.resolve(fileName);
        Properties properties = new Properties();
        properties.setProperty(key, value);
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "test");
        }
        return path;
    }
}
