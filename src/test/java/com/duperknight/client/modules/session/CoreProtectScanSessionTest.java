package com.duperknight.client.modules.session;

import com.duperknight.client.parser.CoreProtectLookupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreProtectScanSessionTest {
    @Test
    void commitsOnlyAResponseMarkerMatchingTheRequestedPage() {
        CoreProtectScanSession session = containerSession("*");

        session.accept("Alice removed x7 minecraft:stone");
        session.accept("page 2/3");
        assertTrue(session.snapshot().aggregates().isEmpty());
        assertEquals(0, session.snapshot().confirmedPages());

        session.accept("Alice removed x2 minecraft:stone page 1/3");
        assertEquals(1, session.snapshot().confirmedPages());
        assertEquals(2, session.snapshot().aggregates().getFirst().count());

        CoreProtectScanSession.Action request = nextPage(session);
        assertEquals(2, request.page());
        session.pageRequested(2);

        session.accept("Bob added x99 chest page 1/3");
        session.accept("Alice added x3 chest page 2/3");
        assertEquals(2, session.snapshot().confirmedPages());
        assertEquals(2, session.snapshot().aggregates().size());
        assertTrue(session.snapshot().aggregates().stream().noneMatch(entry -> entry.player().equals("Bob")));
    }

    @Test
    void scansThroughPageOneThousandThenReportsTheRealTotalAsTruncated() {
        CoreProtectScanSession session = containerSession("*");
        session.accept("page 1/1001");

        CoreProtectScanSession.Action terminal = null;
        for (int page = 2; page <= CoreProtectScanSession.MAX_PAGES; page++) {
            CoreProtectScanSession.Action request = nextPage(session);
            assertEquals(page, request.page());
            session.pageRequested(page);
            terminal = session.accept("page " + page + "/1001");
        }

        assertEquals(CoreProtectScanSession.ActionType.FINISH, terminal.type());
        assertEquals(CoreProtectScanSession.Completion.PAGE_LIMIT, terminal.completion());
        assertEquals(1_000, session.snapshot().confirmedPages());
        assertEquals(1_001, session.snapshot().totalPages());
    }

    @Test
    void stopsAtTwentyMinutesEvenWhenOutputKeepsAvoidingTheSilenceTimeout() {
        CoreProtectScanSession session = containerSession("*");
        CoreProtectScanSession.Action terminal = null;

        for (int tick = 0; tick < CoreProtectScanSession.MAX_DURATION_TICKS; tick++) {
            if (tick % 50 == 0) {
                session.accept("Alice removed x1 stone");
            }
            CoreProtectScanSession.Action action = session.tick();
            if (action.type() == CoreProtectScanSession.ActionType.FINISH) {
                terminal = action;
                break;
            }
        }

        assertEquals(CoreProtectScanSession.Completion.TIME_LIMIT, terminal.completion());
        assertEquals(CoreProtectScanSession.MAX_DURATION_TICKS, session.snapshot().elapsedTicks());
        assertTrue(session.snapshot().hasUnconfirmedData());
    }

    @Test
    void stopsBeforeAddingTheTenThousandAndFirstDistinctAggregate() {
        CoreProtectScanSession session = containerSession("*");
        for (int index = 0; index < CoreProtectScanSession.MAX_AGGREGATES; index++) {
            CoreProtectScanSession.Action action = session.accept(
                    "Alice removed x1 item_" + index);
            assertEquals(CoreProtectScanSession.ActionType.NONE, action.type());
        }

        CoreProtectScanSession.Action capped = session.accept("Alice removed x1 one_too_many");

        assertEquals(CoreProtectScanSession.Completion.AGGREGATE_LIMIT, capped.completion());
        assertEquals(CoreProtectScanSession.MAX_AGGREGATES, session.snapshot().distinctAggregates());
        assertEquals(CoreProtectScanSession.MAX_AGGREGATES, session.snapshot().aggregates().size());
    }

    @Test
    void cancellationAndMarkerlessSilencePreserveParsedDataAsUnconfirmed() {
        CoreProtectScanSession cancelled = containerSession("*");
        cancelled.accept("Alice removed x4 stone");
        cancelled.stop(CoreProtectScanSession.Completion.CANCELLED);
        assertEquals(4, cancelled.snapshot().aggregates().getFirst().count());
        assertEquals(0, cancelled.snapshot().confirmedPages());
        assertTrue(cancelled.snapshot().hasUnconfirmedData());

        CoreProtectScanSession silent = containerSession("*");
        silent.accept("Alice added x2 chest");
        CoreProtectScanSession.Action terminal = tick(silent, CoreProtectScanSession.UNMARKED_SILENCE_TICKS);
        assertEquals(CoreProtectScanSession.Completion.UNCONFIRMED_PAGE, terminal.completion());
        assertEquals(2, silent.snapshot().aggregates().getFirst().count());

        CoreProtectScanSession dispatchFailed = containerSession("*");
        dispatchFailed.accept("Alice removed x6 hopper");
        dispatchFailed.stop(CoreProtectScanSession.Completion.DISPATCH_BLOCKED);
        assertEquals(6, dispatchFailed.snapshot().aggregates().getFirst().count());
        assertEquals(CoreProtectScanSession.Completion.DISPATCH_BLOCKED,
                dispatchFailed.snapshot().completion());
    }

    @Test
    void timesOutWithoutDataAndFailsClosedOnCountOverflow() {
        CoreProtectScanSession timedOut = containerSession("*");
        CoreProtectScanSession.Action timeout = tick(timedOut, CoreProtectScanSession.PAGE_TIMEOUT_TICKS);
        assertEquals(CoreProtectScanSession.Completion.TIMEOUT, timeout.completion());
        assertTrue(timedOut.snapshot().aggregates().isEmpty());

        CoreProtectScanSession overflow = containerSession("*");
        overflow.accept("Alice removed x9223372036854775807 stone");
        CoreProtectScanSession.Action malformed = overflow.accept("Alice removed x1 stone");
        assertEquals(CoreProtectScanSession.Completion.MALFORMED, malformed.completion());
        assertEquals(Long.MAX_VALUE, overflow.snapshot().aggregates().getFirst().count());
    }

    @Test
    void targetFilterStillCompletesAValidEmptyPage() {
        CoreProtectScanSession session = containerSession("Alice");
        CoreProtectScanSession.Action done = session.accept("Bob removed x3 stone page 1/1");

        assertEquals(CoreProtectScanSession.Completion.COMPLETE, done.completion());
        assertTrue(session.snapshot().aggregates().isEmpty());
        assertEquals(1, session.snapshot().confirmedPages());
        assertFalse(session.snapshot().hasUnconfirmedData());
    }

    @Test
    void blockScanUsesTheSameSafeAggregationLifecycle() {
        CoreProtectScanSession session = new CoreProtectScanSession(
                CoreProtectLookupParser.ScanKind.BLOCK, "*");
        CoreProtectScanSession.Action done = session.accept("Bob broke minecraft:stone page 1/1");

        assertEquals(CoreProtectScanSession.Completion.COMPLETE, done.completion());
        assertEquals(new CoreProtectScanSession.Aggregate("Bob", "broke", "stone", 1),
                session.snapshot().aggregates().getFirst());
    }

    private static CoreProtectScanSession containerSession(String target) {
        return new CoreProtectScanSession(CoreProtectLookupParser.ScanKind.CONTAINER, target);
    }

    private static CoreProtectScanSession.Action nextPage(CoreProtectScanSession session) {
        return tick(session, CoreProtectScanSession.PAGE_GAP_TICKS);
    }

    private static CoreProtectScanSession.Action tick(CoreProtectScanSession session, int ticks) {
        CoreProtectScanSession.Action result = null;
        for (int index = 0; index < ticks; index++) {
            result = session.tick();
        }
        return result;
    }
}
