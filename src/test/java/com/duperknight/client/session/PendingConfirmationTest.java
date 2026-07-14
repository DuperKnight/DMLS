package com.duperknight.client.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingConfirmationTest {
    @Test
    void validTokenConsumesFrozenRequestExactlyOnce() {
        long[] now = { 10L };
        PendingConfirmation<String> pending = new PendingConfirmation<>(
                "frozen", 3, Duration.ofSeconds(30), () -> now[0]);

        assertEquals(PendingConfirmation.ConsumeStatus.INVALID_TOKEN,
                pending.consume("wrong").status());
        assertTrue(pending.isActive());

        PendingConfirmation.ConsumeResult<String> confirmed = pending.consume(pending.token());
        assertEquals(PendingConfirmation.ConsumeStatus.CONFIRMED, confirmed.status());
        assertEquals("frozen", confirmed.request().orElseThrow());
        assertEquals(PendingConfirmation.ConsumeStatus.ALREADY_CONSUMED,
                pending.consume(pending.token()).status());
        assertFalse(pending.isActive());
    }

    @Test
    void expiresAtTickOrWallClockBoundary() {
        long[] now = { 0L };
        PendingConfirmation<String> byTick = new PendingConfirmation<>(
                "request", 2, Duration.ofSeconds(30), () -> now[0]);
        assertTrue(byTick.tick());
        assertFalse(byTick.tick());
        assertEquals(PendingConfirmation.ConsumeStatus.EXPIRED,
                byTick.consume(byTick.token()).status());

        PendingConfirmation<String> byClock = new PendingConfirmation<>(
                "request", 600, Duration.ofNanos(10), () -> now[0]);
        now[0] = 10L;
        assertFalse(byClock.isActive());
        assertEquals(PendingConfirmation.ConsumeStatus.EXPIRED,
                byClock.consume(byClock.token()).status());
    }

    @Test
    void invalidationPreventsLaterExecution() {
        PendingConfirmation<String> pending = new PendingConfirmation<>("request");
        pending.invalidate();
        assertEquals(PendingConfirmation.ConsumeStatus.INVALIDATED,
                pending.consume(pending.token()).status());
    }
}
