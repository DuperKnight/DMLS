package com.duperknight.client.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionSnapshotTest {
    @Test
    void reconnectToSameNormalizedHostIsStillAConnectionChange() {
        Object firstHandler = new Object();
        ConnectionSnapshot original = ConnectionSnapshot.connected("PLAY.STONEWORKS.GG:25565", firstHandler);

        assertTrue(original.sameConnection(ConnectionSnapshot.connected("play.stoneworks.gg:25565", firstHandler)));
        assertFalse(original.sameConnection(ConnectionSnapshot.connected("play.stoneworks.gg:25565", new Object())));
    }

    @Test
    void disconnectedSnapshotsMatchOnlyOtherDisconnectedSnapshots() {
        assertTrue(ConnectionSnapshot.disconnected().sameConnection(ConnectionSnapshot.disconnected()));
        assertFalse(ConnectionSnapshot.disconnected().sameConnection(
                ConnectionSnapshot.connected("play.stoneworks.gg", new Object())));
    }
}
