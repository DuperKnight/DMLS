package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XrayRollbackRequestTest {
    @Test
    void freezesEveryRollbackAndBalanceCommandInOrder() {
        var request = XrayRollbackModule.prepare(" Player_1 ");

        assertTrue(request.valid());
        assertEquals("Player_1", request.username());
        assertEquals(4, request.commands().size());
        assertTrue(request.commands().get(0).startsWith("co rollback u:Player_1 t:30d r:#global a:block i:"));
        assertTrue(request.commands().get(1).startsWith("co rollback u:Player_1 t:30d r:#global a:block i:"));
        assertFalse(request.commands().get(0).contains(", "));
        assertFalse(request.commands().get(1).contains(", "));
        assertEquals("co rollback u:Player_1 t:7d r:#global a:container", request.commands().get(2));
        assertEquals("bal Player_1", request.commands().get(3));
    }

    @Test
    void rejectsInvalidUsernameWithoutProducingCommands() {
        var request = XrayRollbackModule.prepare("not valid");

        assertFalse(request.valid());
        assertEquals(XrayRollbackModule.PreparationStatus.INVALID_USERNAME, request.status());
        assertTrue(request.commands().isEmpty());
    }
}
