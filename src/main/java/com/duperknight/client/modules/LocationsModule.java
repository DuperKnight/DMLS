package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.modules.LocationsScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.AtomicProperties;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

/** Saves named locations client-side and teleports back to them on click. */
public final class LocationsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Locations§8] §7";
    private static final int MAX_NAME_LENGTH = 32;

    private final Path path;
    private final Supplier<List<String>> allowedServers;
    private final Map<String, SavedLocation> locations = new LinkedHashMap<>();

    /** The result of a location action. */
    public enum Outcome {
        SAVED,
        UPDATED,
        DELETED,
        SENT,
        SIMULATED,
        INVALID,
        NOT_FOUND,
        RANK_BLOCKED,
        SERVER_BLOCKED,
        UNBOUND,
        WRONG_SERVER,
        WRONG_WORLD,
        IO_ERROR
    }

    /** Whether a saved location is safe to use on the current connection and dimension. */
    public enum TeleportCompatibility {
        READY,
        UNBOUND,
        WRONG_SERVER,
        WRONG_WORLD
    }

    /** One saved location. An empty server denotes an unbound legacy entry. */
    public record SavedLocation(String server, String world, int x, int y, int z) {
        public SavedLocation {
            server = normalizeServerIdentity(Objects.requireNonNull(server, "server"));
            world = Objects.requireNonNull(world, "world").trim();
        }

        public boolean isBound() {
            return !server.isEmpty();
        }
    }

    public LocationsModule() {
        this(defaultPath(), DMLSConfig::allowedServers);
    }

    LocationsModule(Path path, Supplier<List<String>> allowedServers) {
        super(StaffRank.HELPER);
        this.path = Objects.requireNonNull(path, "path");
        this.allowedServers = Objects.requireNonNull(allowedServers, "allowedServers");
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.locations.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.FILLED_MAP);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.locations.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new LocationsScreen(parent, this));
    }

    @Override
    public void register() {
        load();
    }

    /** Returns the names of all saved locations. */
    public List<String> names() {
        return List.copyOf(locations.keySet());
    }

    /** Returns an insertion-ordered snapshot of all saved locations. */
    public Map<String, SavedLocation> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(locations));
    }

    /** Saves the player's current position under the given name. */
    public Outcome save(MinecraftClient client, String name) {
        if (!hasRequiredRank(client)) {
            return Outcome.RANK_BLOCKED;
        }

        String trimmed = normalizeName(name);
        if (!isValidName(trimmed)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.invalid_name", MAX_NAME_LENGTH);
            return Outcome.INVALID;
        }

        ServerGuard.GuardResult guard = ServerGuard.check(client);
        if (!guard.allowed()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                    guard.reason(), guard.address());
            return Outcome.SERVER_BLOCKED;
        }

        BlockPos pos = client.player.getBlockPos();
        String world = client.world.getRegistryKey().getValue().toString();
        Outcome outcome = storeLocation(trimmed,
                new SavedLocation(guard.address(), world, pos.getX(), pos.getY(), pos.getZ()));
        if (outcome == Outcome.IO_ERROR) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.io_error");
            return outcome;
        }

        ChatUtils.sendTranslatedMessage(client, PREFIX,
                outcome == Outcome.UPDATED ? "dmls.chat.locations.updated" : "dmls.chat.locations.saved",
                trimmed, pos.getX() + " " + pos.getY() + " " + pos.getZ(), world);
        return outcome;
    }

    /** Teleports to the given saved location when its server and dimension match. */
    public Outcome teleport(MinecraftClient client, String name) {
        if (!hasRequiredRank(client)) {
            return Outcome.RANK_BLOCKED;
        }

        String trimmed = normalizeName(name);
        SavedLocation location = locations.get(trimmed);
        if (location == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.unknown", trimmed);
            return Outcome.NOT_FOUND;
        }

        // A location preview is safe while offline: captured dry-run prints the exact
        // command and can never turn into a live teleport. Matching server and dimension
        // remain mandatory below for every live dispatch.
        if (DMLSConfig.dryRun()) {
            CommandDispatch dispatch = ClientUtils.dispatchCommand(
                    client, "tp %s %s %s".formatted(location.x(), location.y(), location.z()));
            return dispatch == CommandDispatch.SIMULATED ? Outcome.SIMULATED : Outcome.SERVER_BLOCKED;
        }

        ServerGuard.GuardResult guard = ServerGuard.check(client);
        if (!guard.allowed()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                    guard.reason(), guard.address());
            return Outcome.SERVER_BLOCKED;
        }

        String currentWorld = client.world.getRegistryKey().getValue().toString();
        TeleportCompatibility compatibility = compatibility(location, guard.address(), currentWorld);
        switch (compatibility) {
            case UNBOUND -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.unbound", trimmed);
                return Outcome.UNBOUND;
            }
            case WRONG_SERVER -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.wrong_server",
                        trimmed, location.server(), guard.address());
                return Outcome.WRONG_SERVER;
            }
            case WRONG_WORLD -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.wrong_world",
                        trimmed, location.world(), currentWorld);
                return Outcome.WRONG_WORLD;
            }
            case READY -> {
                // Continue below. ClientUtils performs one final allowlist/connection check at dispatch time.
            }
        }

        CommandDispatch dispatch = ClientUtils.dispatchCommand(
                client, "tp %s %s %s".formatted(location.x(), location.y(), location.z()));
        if (dispatch == CommandDispatch.SENT) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.teleporting",
                    trimmed, location.x() + " " + location.y() + " " + location.z(), location.world());
            return Outcome.SENT;
        }
        if (dispatch == CommandDispatch.SIMULATED) {
            return Outcome.SIMULATED;
        }

        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
        return Outcome.SERVER_BLOCKED;
    }

    /** Deletes the given saved location. The previous list remains in memory if persistence fails. */
    public Outcome delete(MinecraftClient client, String name) {
        if (!hasRequiredRank(client)) {
            return Outcome.RANK_BLOCKED;
        }

        String trimmed = normalizeName(name);
        Outcome outcome = deleteLocation(trimmed);
        if (outcome == Outcome.NOT_FOUND) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.unknown", trimmed);
        } else if (outcome == Outcome.IO_ERROR) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.io_error");
        } else {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.deleted", trimmed);
        }
        return outcome;
    }

    /** Lists all saved locations as clickable chat lines. */
    public void list(MinecraftClient client) {
        if (!hasRequiredRank(client)) {
            return;
        }
        if (locations.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.empty");
            return;
        }

        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.header", locations.size());
        locations.forEach((name, location) -> {
            Text nameText = Text.literal(name).formatted(Formatting.GOLD).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/dmls loc tp " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.locations.tp_hover", name))));
            Text deleteText = Text.translatable("dmls.chat.locations.delete_button").formatted(Formatting.RED).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/dmls loc del " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.locations.delete_hover", name))));
            String server = location.isBound() ? location.server() : "unbound";
            ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(nameText)
                    .append(Text.literal(" §8(" + location.x() + ", " + location.y() + ", " + location.z()
                            + " in " + location.world() + " @ " + server + ") "))
                    .append(deleteText));
        });
    }

    /** Pure compatibility check used immediately before a live teleport. */
    public static TeleportCompatibility compatibility(SavedLocation location, String currentServer, String currentWorld) {
        Objects.requireNonNull(location, "location");
        if (!location.isBound()) {
            return TeleportCompatibility.UNBOUND;
        }
        if (!location.server().equals(normalizeServerIdentity(currentServer))) {
            return TeleportCompatibility.WRONG_SERVER;
        }
        if (!location.world().equals(Objects.requireNonNullElse(currentWorld, "").trim())) {
            return TeleportCompatibility.WRONG_WORLD;
        }
        return TeleportCompatibility.READY;
    }

    Outcome storeLocation(String name, SavedLocation location) {
        String trimmed = normalizeName(name);
        if (!isValidName(trimmed) || !isValidLocation(location)) {
            return Outcome.INVALID;
        }

        Map<String, SavedLocation> candidate = new LinkedHashMap<>(locations);
        boolean existed = candidate.put(trimmed, location) != null;
        if (!persist(candidate)) {
            return Outcome.IO_ERROR;
        }
        replaceLocations(candidate);
        return existed ? Outcome.UPDATED : Outcome.SAVED;
    }

    Outcome deleteLocation(String name) {
        String trimmed = normalizeName(name);
        if (!locations.containsKey(trimmed)) {
            return Outcome.NOT_FOUND;
        }

        Map<String, SavedLocation> candidate = new LinkedHashMap<>(locations);
        candidate.remove(trimmed);
        if (!persist(candidate)) {
            return Outcome.IO_ERROR;
        }
        replaceLocations(candidate);
        return Outcome.DELETED;
    }

    void load() {
        if (!Files.exists(path)) {
            locations.clear();
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}", path, e);
            return;
        }

        String legacyServer = singleExactAllowedServer(allowedServers.get()).orElse("");
        Map<String, SavedLocation> loaded = new LinkedHashMap<>();
        boolean migrationRequired = false;
        for (String name : properties.stringPropertyNames()) {
            String trimmed = normalizeName(name);
            if (!isValidName(trimmed)) {
                DMLS.LOGGER.warn("Ignoring invalid saved location name in {}", path);
                continue;
            }
            String serialized = properties.getProperty(name, "");
            Optional<SavedLocation> parsed = parseLocation(serialized, legacyServer);
            if (parsed.isPresent()) {
                loaded.put(trimmed, parsed.get());
                if (serialized.split(";", -1).length == 4) {
                    migrationRequired = true;
                }
            }
        }
        if (migrationRequired && !persist(loaded)) {
            DMLS.LOGGER.warn("Loaded legacy locations from {}, but could not migrate them to the server-bound format", path);
        }
        replaceLocations(loaded);
    }

    static Optional<String> singleExactAllowedServer(List<String> rules) {
        if (rules == null) {
            return Optional.empty();
        }
        List<String> exact = rules.stream()
                .map(ServerGuard::normalizeRule)
                .flatMap(Optional::stream)
                .filter(rule -> !rule.startsWith("*."))
                .distinct()
                .toList();
        return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
    }

    static Optional<SavedLocation> parseLocation(String serialized, String legacyServer) {
        String[] parts = Objects.requireNonNullElse(serialized, "").split(";", -1);
        try {
            if (parts.length == 4 && isValidWorld(parts[0])) {
                return Optional.of(new SavedLocation(Objects.requireNonNullElse(legacyServer, ""), parts[0],
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            }
            if (parts.length == 5 && isValidStoredServer(parts[0]) && isValidWorld(parts[1])) {
                return Optional.of(new SavedLocation(parts[0], parts[1],
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
            }
        } catch (NumberFormatException ignored) {
            // A corrupt row is ignored without affecting other saved locations.
        }
        return Optional.empty();
    }

    private boolean persist(Map<String, SavedLocation> candidate) {
        Properties properties = new Properties();
        candidate.forEach((name, location) -> properties.setProperty(name,
                "%s;%s;%s;%s;%s".formatted(location.server(), location.world(),
                        location.x(), location.y(), location.z())));
        try {
            AtomicProperties.store(path, properties, "DMLS saved locations");
            return true;
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to save {}", path, e);
            return false;
        }
    }

    private void replaceLocations(Map<String, SavedLocation> replacement) {
        locations.clear();
        locations.putAll(replacement);
    }

    private static boolean isValidLocation(SavedLocation location) {
        return location != null && isValidStoredServer(location.server()) && isValidWorld(location.world());
    }

    private static boolean isValidStoredServer(String server) {
        if (server == null || server.isEmpty()) {
            return true;
        }
        Optional<String> normalized = ServerGuard.normalizeRule(server);
        return normalized.isPresent() && !normalized.get().startsWith("*.")
                && normalized.get().equals(ServerGuard.normalizeAddress(server));
    }

    private static boolean isValidWorld(String world) {
        return world != null && !world.isBlank() && !world.contains(";")
                && world.chars().noneMatch(Character::isISOControl);
    }

    private static String normalizeName(String name) {
        return Objects.requireNonNullElse(name, "").trim();
    }

    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH
                && name.chars().noneMatch(Character::isISOControl);
    }

    private static String normalizeServerIdentity(String server) {
        String normalized = ServerGuard.normalizeAddress(server);
        int colon = normalized.lastIndexOf(':');
        if (colon > 0 && normalized.indexOf(':') == colon && normalized.substring(colon + 1).equals("25565")) {
            return normalized.substring(0, colon);
        }
        return normalized;
    }

    private static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls-locations.properties");
    }
}
