package com.duperknight.client.accountlink;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Non-blocking, in-memory availability state for UI and command requirements. */
public final class DiscordLinkAvailability {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private DiscordLinkAvailability() {
    }

    public static boolean isLinked(Minecraft client) {
        if (client == null || client.getUser().getProfileId() == null) return false;
        return isLinked(client.getUser().getProfileId());
    }

    public static boolean isLinked(UUID minecraftUuid) {
        return minecraftUuid != null && STATES.get(minecraftUuid) == State.LINKED;
    }

    /** Starts a remote session validation without trusting cached profile data as proof of a current link. */
    public static void warmUp(Minecraft client) {
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
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                ClientCommands.refreshCommandCompletions();
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
