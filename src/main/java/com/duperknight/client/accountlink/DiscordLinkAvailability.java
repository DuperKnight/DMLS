package com.duperknight.client.accountlink;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Non-blocking, in-memory availability state for UI and command requirements. */
public final class DiscordLinkAvailability {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private DiscordLinkAvailability() {
    }

    public static boolean isLinked(MinecraftClient client) {
        if (client == null || client.getSession().getUuidOrNull() == null) return false;
        return isLinked(client.getSession().getUuidOrNull());
    }

    public static boolean isLinked(UUID minecraftUuid) {
        return minecraftUuid != null && STATES.get(minecraftUuid) == State.LINKED;
    }

    /** Starts a remote session validation without trusting cached profile data as proof of a current link. */
    public static void warmUp(MinecraftClient client) {
        DiscordLinkSessionValidator.validateSavedLink(client);
    }

    public static void markLinked(UUID minecraftUuid) {
        if (minecraftUuid != null) setState(minecraftUuid, State.LINKED);
    }

    public static void markUnlinked(UUID minecraftUuid) {
        if (minecraftUuid != null) setState(minecraftUuid, State.UNLINKED);
    }

    static void clearForTests() {
        STATES.clear();
    }

    private static void setState(UUID minecraftUuid, State state) {
        State previous = STATES.put(minecraftUuid, state);
        if (previous != state) refreshCommandCompletions();
    }

    private static void refreshCommandCompletions() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                ClientCommandManager.refreshCommandCompletions();
            } catch (IllegalStateException ignored) {
                // Normal before joining a server or before its command tree has arrived.
            }
        });
    }

    private enum State {
        LINKED,
        UNLINKED
    }
}
