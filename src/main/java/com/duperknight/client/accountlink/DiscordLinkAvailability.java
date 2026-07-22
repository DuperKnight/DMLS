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

    /** Optimistically restores the last confirmed local link until the API explicitly revokes it. */
    public static void warmUp(MinecraftClient client) {
        if (client == null || client.getSession().getUuidOrNull() == null) return;
        UUID minecraftUuid = client.getSession().getUuidOrNull();
        boolean locallyConfirmed = DiscordLinkTokenStore.load(minecraftUuid).isPresent()
                && DiscordAccountProfileStore.load(minecraftUuid).isPresent();
        setState(minecraftUuid, locallyConfirmed ? State.LINKED : State.UNLINKED);
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
