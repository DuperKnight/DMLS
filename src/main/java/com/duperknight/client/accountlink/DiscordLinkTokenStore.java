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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persists Appwrite client tokens locally without putting them in the general DMLS settings file. */
public final class DiscordLinkTokenStore {
    private static final Pattern CLIENT_TOKEN = Pattern.compile("dmls_[A-Za-z0-9_-]{43}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private DiscordLinkTokenStore() {
    }

    public static synchronized boolean save(UUID minecraftUuid, String clientToken) {
        Path tokenFile = FabricLoader.getInstance().getConfigDir().resolve("dmls-account-links.json");
        return saveTo(tokenFile, minecraftUuid, clientToken);
    }

    public static synchronized Optional<String> load(UUID minecraftUuid) {
        Path tokenFile = FabricLoader.getInstance().getConfigDir().resolve("dmls-account-links.json");
        return loadFrom(tokenFile, minecraftUuid);
    }

    public static synchronized boolean delete(UUID minecraftUuid) {
        Path tokenFile = FabricLoader.getInstance().getConfigDir().resolve("dmls-account-links.json");
        return deleteFrom(tokenFile, minecraftUuid);
    }

    static boolean saveTo(Path tokenFile, UUID minecraftUuid, String clientToken) {
        if (minecraftUuid == null || clientToken == null || !CLIENT_TOKEN.matcher(clientToken).matches()) {
            return false;
        }

        try {
            Files.createDirectories(tokenFile.getParent());
            JsonObject root = readExisting(tokenFile);
            JsonObject tokens = objectOrNew(root, "tokens");
            tokens.addProperty(minecraftUuid.toString().toLowerCase(), clientToken);
            root.addProperty("version", 1);
            root.add("tokens", tokens);
            writeRoot(tokenFile, root);
            return true;
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not save the Discord link token", error);
            return false;
        }
    }

    static Optional<String> loadFrom(Path tokenFile, UUID minecraftUuid) {
        if (minecraftUuid == null || !Files.exists(tokenFile)) return Optional.empty();
        try {
            JsonObject root = readExisting(tokenFile);
            JsonElement tokensElement = root.get("tokens");
            if (tokensElement == null || !tokensElement.isJsonObject()) return Optional.empty();
            JsonElement tokenElement = tokensElement.getAsJsonObject().get(minecraftUuid.toString().toLowerCase());
            if (tokenElement == null || !tokenElement.isJsonPrimitive()) return Optional.empty();
            String token = tokenElement.getAsString();
            return CLIENT_TOKEN.matcher(token).matches() ? Optional.of(token) : Optional.empty();
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not read the Discord link token", error);
            return Optional.empty();
        }
    }

    static boolean deleteFrom(Path tokenFile, UUID minecraftUuid) {
        if (minecraftUuid == null || !Files.exists(tokenFile)) return true;
        try {
            JsonObject root = readExisting(tokenFile);
            JsonObject tokens = objectOrNew(root, "tokens");
            tokens.remove(minecraftUuid.toString().toLowerCase());
            root.addProperty("version", 1);
            root.add("tokens", tokens);
            writeRoot(tokenFile, root);
            return true;
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not delete the Discord link token", error);
            return false;
        }
    }

    private static void writeRoot(Path tokenFile, JsonObject root) throws IOException {
        Path temporary = Files.createTempFile(tokenFile.getParent(), "dmls-account-links-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            setOwnerOnlyPermissions(temporary);
            moveIntoPlace(temporary, tokenFile);
            setOwnerOnlyPermissions(tokenFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject readExisting(Path tokenFile) throws IOException {
        if (!Files.exists(tokenFile)) return new JsonObject();
        JsonElement parsed = JsonParser.parseString(Files.readString(tokenFile, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IOException("Discord link token file is not a JSON object");
        return parsed.getAsJsonObject();
    }

    private static JsonObject objectOrNew(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setOwnerOnlyPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // Windows and other non-POSIX file systems apply their own per-user access controls.
        }
    }
}
