package com.duperknight.client.utils;

import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientUtilsDispatchTest {
    @Test
    void capturedDryRunCanSimulateWhileOffline() {
        assertEquals(CommandDispatch.SIMULATED, ClientUtils.dispatchCommand(
                null, "co rollback u:test", true, ConnectionSnapshot.disconnected()));
        assertEquals(CommandDispatch.SIMULATED, ClientUtils.dispatchChatMessage(
                null, "Hello", true, ConnectionSnapshot.disconnected()));
    }

    @Test
    void liveOfflineAndInvalidPayloadsAreBlocked() {
        assertEquals(CommandDispatch.BLOCKED, ClientUtils.dispatchCommand(
                null, "co rollback u:test", false, ConnectionSnapshot.disconnected()));
        assertEquals(CommandDispatch.BLOCKED, ClientUtils.dispatchCommand(
                null, " ", true, ConnectionSnapshot.disconnected()));
        assertEquals(CommandDispatch.BLOCKED, ClientUtils.dispatchChatMessage(
                null, "", true, ConnectionSnapshot.disconnected()));
    }
}
