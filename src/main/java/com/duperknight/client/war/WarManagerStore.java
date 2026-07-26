package com.duperknight.client.war;

import com.duperknight.DMLS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic JSON persistence for timers and exact workflow checkpoints. */
public final class WarManagerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    public WarManagerStore() {
        this(FabricLoader.getInstance().getConfigDir().resolve("dmls-war-manager.json"));
    }

    public WarManagerStore(Path path) {
        this.path = path;
    }

    public WarManagerState load() {
        if (!Files.exists(path)) return new WarManagerState();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WarManagerState state = GSON.fromJson(reader, WarManagerState.class);
            if (state == null || state.version != WarManagerState.CURRENT_VERSION) {
                throw new JsonParseException("Unsupported War Manager state version");
            }
            state.normalize();
            return state;
        } catch (IOException | JsonParseException exception) {
            DMLS.LOGGER.error("Could not load {}; War Manager automation is paused", path, exception);
            WarManagerState state = new WarManagerState();
            WarManagerState.War marker = new WarManagerState.War();
            marker.id = "storage-error";
            marker.status = WarManagerState.Status.PAUSED;
            marker.error = "Stored War Manager state is unreadable; preserve and repair " + path.getFileName();
            state.wars.add(marker);
            return state;
        }
    }

    public boolean save(WarManagerState state) {
        state.normalize();
        Path parent = path.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".dmls-war-manager-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            DMLS.LOGGER.error("Could not save {}; no further War Manager command will be sent", path, exception);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    Path path() {
        return path;
    }
}
