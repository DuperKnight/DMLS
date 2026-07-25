package com.duperknight.client.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundSpamSafetyTest {
    @BeforeEach
    void reset() {
        OutboundSpamSafety.reset();
    }

    @Test
    void mirrorsTwentyPointDebtAndOnePointTickDecay() {
        OutboundSpamSafety.recordOutbound();
        assertEquals(20, OutboundSpamSafety.debt());
        OutboundSpamSafety.tick();
        assertEquals(19, OutboundSpamSafety.debt());
    }

    @Test
    void nonAdminRequiresTwentyTicksBetweenCommands() {
        OutboundSpamSafety.recordOutbound();
        assertFalse(OutboundSpamSafety.canDispatch(false));
        for (int tick = 0; tick < 19; tick++) OutboundSpamSafety.tick();
        assertFalse(OutboundSpamSafety.canDispatch(false));
        OutboundSpamSafety.tick();
        assertTrue(OutboundSpamSafety.canDispatch(false));
    }

    @Test
    void limiterNeverPermitsPostSendDebtAboveConservativeCeiling() {
        for (int command = 0; command < 8; command++) {
            while (!OutboundSpamSafety.canDispatch(false)) OutboundSpamSafety.tick();
            OutboundSpamSafety.recordOutbound();
            assertTrue(OutboundSpamSafety.debt() <= OutboundSpamSafety.SAFE_CEILING);
        }
    }

    @Test
    void manualOutboundExtendsWaitAndAdminBypassesExtraDelay() {
        OutboundSpamSafety.recordOutbound();
        for (int tick = 0; tick < 10; tick++) OutboundSpamSafety.tick();
        int beforeManual = OutboundSpamSafety.ticksUntilSafe(false);
        OutboundSpamSafety.recordOutbound();

        assertTrue(OutboundSpamSafety.ticksUntilSafe(false) > beforeManual);
        assertTrue(OutboundSpamSafety.canDispatch(true));
        assertEquals(0, OutboundSpamSafety.ticksUntilSafe(true));
    }

    @Test
    void adminOutboundStillContributesToGlobalDebt() {
        assertTrue(OutboundSpamSafety.canDispatch(true));
        OutboundSpamSafety.recordOutbound();

        assertEquals(OutboundSpamSafety.COST, OutboundSpamSafety.debt());
        assertTrue(OutboundSpamSafety.canDispatch(true));
    }

    @Test
    void resetClearsConnectionScopedDebt() {
        OutboundSpamSafety.recordOutbound();
        OutboundSpamSafety.reset();

        assertEquals(0, OutboundSpamSafety.debt());
        assertTrue(OutboundSpamSafety.canDispatch(false));
    }
}
