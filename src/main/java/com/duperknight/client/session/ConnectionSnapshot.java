package com.duperknight.client.session;

import com.duperknight.client.utils.ServerGuard;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/**
 * Identity of the connection on which an operation started.
 *
 * <p>The network handler is deliberately compared by reference. A reconnect to the same
 * normalized address must not make an old response-tracked operation valid again.</p>
 */
public final class ConnectionSnapshot {
    private static final ConnectionSnapshot DISCONNECTED = new ConnectionSnapshot("", null, false);

    private final String serverAddress;
    private final Object networkHandlerIdentity;
    private final boolean connected;

    private ConnectionSnapshot(String serverAddress, Object networkHandlerIdentity, boolean connected) {
        this.serverAddress = ServerGuard.normalizeAddress(serverAddress);
        this.networkHandlerIdentity = networkHandlerIdentity;
        this.connected = connected;
    }

    public static ConnectionSnapshot capture(Minecraft client) {
        if (client == null || client.isLocalServer() || client.player == null || client.level == null
                || client.getConnection() == null || !client.getConnection().isAcceptingMessages()
                || client.getCurrentServer() == null) {
            return DISCONNECTED;
        }
        return connected(client.getCurrentServer().ip, client.getConnection());
    }

    /** Creates a snapshot around a caller-owned identity object, primarily for adapters and tests. */
    public static ConnectionSnapshot connected(String serverAddress, Object networkHandlerIdentity) {
        Objects.requireNonNull(networkHandlerIdentity, "networkHandlerIdentity");
        String normalized = ServerGuard.normalizeAddress(serverAddress);
        if (normalized.isEmpty()) throw new IllegalArgumentException("serverAddress");
        return new ConnectionSnapshot(normalized, networkHandlerIdentity, true);
    }

    public static ConnectionSnapshot disconnected() {
        return DISCONNECTED;
    }

    public String serverAddress() {
        return serverAddress;
    }

    public boolean connected() {
        return connected;
    }

    /** Returns whether this is the exact same live connection (not merely the same host). */
    public boolean sameConnection(ConnectionSnapshot other) {
        Objects.requireNonNull(other, "other");
        if (connected != other.connected) return false;
        if (!connected) return true;
        return serverAddress.equals(other.serverAddress)
                && networkHandlerIdentity == other.networkHandlerIdentity;
    }

    public boolean matches(Minecraft client) {
        return sameConnection(capture(client));
    }

    @Override
    public String toString() {
        return connected ? "ConnectionSnapshot[serverAddress=" + serverAddress + "]"
                : "ConnectionSnapshot[disconnected]";
    }
}
