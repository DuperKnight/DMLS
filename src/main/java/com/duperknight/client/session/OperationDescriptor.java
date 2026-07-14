package com.duperknight.client.session;

import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

/** Immutable metadata and safety policy captured when a managed operation starts. */
public record OperationDescriptor(
        String operationId,
        String displayName,
        ConnectionSnapshot connection,
        boolean dryRunCaptured
) {
    public OperationDescriptor {
        operationId = requireText(operationId, "operationId");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(connection, "connection");
    }

    public static OperationDescriptor capture(
            MinecraftClient client,
            String operationId,
            String displayName
    ) {
        return new OperationDescriptor(operationId, displayName,
                ConnectionSnapshot.capture(client), DMLSConfig.dryRun());
    }

    public boolean connectionStillMatches(MinecraftClient client) {
        return connection.matches(client);
    }

    public boolean connectionStillMatches(ConnectionSnapshot current) {
        return connection.sameConnection(current);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String clean = value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(name);
        return clean;
    }
}
