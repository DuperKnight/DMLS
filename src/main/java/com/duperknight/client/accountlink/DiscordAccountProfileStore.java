package com.duperknight.client.accountlink;

import com.duperknight.DMLS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** Local cache for Discord profile details already returned by a successful link-status check. */
public final class DiscordAccountProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DiscordAccountProfileStore() {
    }

    public static synchronized boolean save(DiscordAccountProfile profile) {
        boolean saved = saveTo(profileFile(), profile);
        if (saved && profile != null) DiscordLinkAvailability.markLinked(profile.minecraftUuid());
        return saved;
    }

    public static synchronized Optional<DiscordAccountProfile> load(UUID minecraftUuid) {
        return loadFrom(profileFile(), minecraftUuid);
    }

    public static synchronized boolean delete(UUID minecraftUuid) {
        boolean deleted = deleteFrom(profileFile(), minecraftUuid);
        if (deleted) DiscordLinkAvailability.markUnlinked(minecraftUuid);
        return deleted;
    }

    static boolean saveTo(Path file, DiscordAccountProfile profile) {
        if (profile == null) return false;
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = readExisting(file);
            JsonObject profiles = objectOrNew(root, "profiles");
            JsonObject value = new JsonObject();
            value.addProperty("discordUserId", profile.discordUserId());
            value.addProperty("discordUsername", profile.discordUsername());
            value.addProperty("discordDisplayName", profile.discordDisplayName());
            value.addProperty("discordAvatarUrl", profile.discordAvatarUrl());
            profiles.add(profile.minecraftUuid().toString(), value);
            root.addProperty("version", 1);
            root.add("profiles", profiles);
            writeRoot(file, root);
            return true;
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not save the Discord account profile", error);
            return false;
        }
    }

    static Optional<DiscordAccountProfile> loadFrom(Path file, UUID minecraftUuid) {
        if (minecraftUuid == null || !Files.exists(file)) return Optional.empty();
        try {
            JsonObject profiles = objectOrNew(readExisting(file), "profiles");
            JsonElement element = profiles.get(minecraftUuid.toString());
            if (element == null || !element.isJsonObject()) return Optional.empty();
            JsonObject value = element.getAsJsonObject();
            return Optional.of(new DiscordAccountProfile(
                    minecraftUuid,
                    string(value, "discordUserId"),
                    string(value, "discordUsername"),
                    string(value, "discordDisplayName"),
                    string(value, "discordAvatarUrl")));
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not read the Discord account profile", error);
            return Optional.empty();
        }
    }

    static boolean deleteFrom(Path file, UUID minecraftUuid) {
        if (minecraftUuid == null || !Files.exists(file)) return true;
        try {
            JsonObject root = readExisting(file);
            JsonObject profiles = objectOrNew(root, "profiles");
            profiles.remove(minecraftUuid.toString());
            root.addProperty("version", 1);
            root.add("profiles", profiles);
            writeRoot(file, root);
            return true;
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not delete the Discord account profile", error);
            return false;
        }
    }

    private static Path profileFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls-discord-profiles.json");
    }

    private static JsonObject readExisting(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IOException("Discord account profile file is not a JSON object");
        return parsed.getAsJsonObject();
    }

    private static JsonObject objectOrNew(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalStateException("Missing " + name);
        return element.getAsString();
    }

    private static void writeRoot(Path file, JsonObject root) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "dmls-discord-profiles-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
