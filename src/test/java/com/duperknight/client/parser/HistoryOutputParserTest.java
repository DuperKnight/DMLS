package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryOutputParserTest {
    @Test void parsesStoneworksHeaderAndMultilineRecordsForTheCurrentUser() {
        var parser = new HistoryOutputParser("DuperKnight");
        parser.accept("History for DuperKnight (Limit: 9):");
        parser.accept(" -- [147 days ago] --\n DuperKnight was muted by GreenBeanZ_: 'reason' [Expired]");
        parser.accept(" -- [147 days ago] --\n DuperKnight was warned by El_Eyeo: 'reason' [Expired]");
        parser.accept(" -- [323 days ago] --\n DuperKnight was kicked by Console: 'reason'");
        parser.accept(" -- [333 days ago] --\n DuperKnight was banned by Console: 'reason'");
        parser.accept("\n DuperKnight was unbanned by Console: 'not a punishment record'");

        assertEquals(HistoryOutputParser.Status.PARSED, parser.result().status());
        assertEquals(1, parser.result().bans());
        assertEquals(1, parser.result().mutes());
        assertEquals(1, parser.result().warns());
        assertEquals(1, parser.result().kicks());
    }

    @Test void headerWithNoFollowingRecordsIsCleanAfterTheQuietWindow() {
        var parser = new HistoryOutputParser("DuperKnight");
        parser.accept("History for DuperKnight (Limit: 10):");
        assertEquals(HistoryOutputParser.Status.CLEAN, parser.result().status());
    }

    @Test void neverJoinedIsNotReportedAsClean() {
        var parser = new HistoryOutputParser("DuperKnight");
        assertEquals(HistoryOutputParser.Event.COMPLETE, parser.accept("User has not joined before."));
        assertEquals(HistoryOutputParser.Status.NEVER_JOINED, parser.result().status());
    }

    @Test void ignoresOtherAccountsAndUnrecognizedMessages() {
        var parser = new HistoryOutputParser("DuperKnight");
        parser.accept("History for AnotherPlayer (Limit: 10):");
        parser.accept(" -- [one day ago] --\n AnotherPlayer was banned by Console: 'reason'");
        assertEquals(HistoryOutputParser.Status.UNKNOWN, parser.result().status());
    }

    @Test void recordsBeforeTheCorrelatedHeaderDoNotCount() {
        var parser = new HistoryOutputParser("DuperKnight");
        assertEquals(HistoryOutputParser.Event.UNRELATED,
                parser.accept("DuperKnight was banned by Console: 'reason'"));
        assertEquals(HistoryOutputParser.Status.UNKNOWN, parser.result().status());
        assertEquals(0, parser.result().bans());
    }

    @Test void malformedCorrelatedHeaderFailsClosed() {
        var parser = new HistoryOutputParser("DuperKnight");
        assertEquals(HistoryOutputParser.Event.FAILED,
                parser.accept("History for DuperKnight (Limit: many):"));
        assertEquals(HistoryOutputParser.Status.MALFORMED, parser.result().status());
    }

    @Test void usernamePrefixesDoNotCorrelateAnotherAccountsHeader() {
        var parser = new HistoryOutputParser("DuperKnight");
        assertEquals(HistoryOutputParser.Event.UNRELATED,
                parser.accept("History for DuperKnight2 (Limit: many):"));
        assertEquals(HistoryOutputParser.Status.UNKNOWN, parser.result().status());
    }

    @Test void emptyOutputRemainsUnknownInsteadOfBeingCalledClean() {
        var parser = new HistoryOutputParser("DuperKnight");
        assertEquals(HistoryOutputParser.Event.UNRELATED, parser.accept(""));
        assertEquals(HistoryOutputParser.Status.UNKNOWN, parser.result().status());
    }
}
