package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AltsOutputParserTest {
    @Test
    void parsesCorrelatedInlineResultsAndIgnoresOtherTargets() {
        AltsOutputParser parser = new AltsOutputParser("Alice");
        assertEquals(AltsOutputParser.Event.UNRELATED, parser.accept("Alts for Bob: OtherOne"));
        assertEquals(AltsOutputParser.Event.INLINE_RESULT,
                parser.accept("Known alts for Alice: Bob, charlie, BOB"));
        assertEquals(List.of("Bob", "charlie"), parser.alts());
    }

    @Test
    void parsesLegendListsAndDistinguishesEmptyOutputFromNoAlts() {
        AltsOutputParser parser = new AltsOutputParser("Alice");
        assertEquals(AltsOutputParser.Event.LIST_STARTED,
                parser.accept("[Online] [Offline] [Banned] [IPBanned]"));
        parser.accept("Alice");
        parser.accept("Bob, Charlie");
        assertEquals(AltsOutputParser.ListStatus.FOUND, parser.finishList().status());
        assertEquals(List.of("Bob", "Charlie"), parser.finishList().alts());

        AltsOutputParser onlyTarget = new AltsOutputParser("Alice");
        onlyTarget.accept("[Online] [Offline] [Banned] [IPBanned]");
        onlyTarget.accept("Alice");
        assertEquals(AltsOutputParser.ListStatus.NO_ALTS, onlyTarget.finishList().status());

        AltsOutputParser empty = new AltsOutputParser("Alice");
        empty.accept("[Online] [Offline] [Banned] [IPBanned]");
        assertEquals(AltsOutputParser.ListStatus.NO_RESPONSE, empty.finishList().status());
    }

    @Test
    void recognizesCorrelatedNoAltsAndMalformedInlineResponses() {
        AltsOutputParser parser = new AltsOutputParser("Alice");
        assertEquals(AltsOutputParser.Event.NO_ALTS, parser.accept("Alice has no known alts"));
        assertEquals(AltsOutputParser.Event.MALFORMED, parser.accept("Alts for Alice: ???"));
        assertEquals(AltsOutputParser.Event.UNRELATED, parser.accept("normal unrelated server output"));
        assertEquals(AltsOutputParser.Event.UNRELATED, parser.accept("Alts for Alice2: Charlie"));
        assertEquals(AltsOutputParser.Event.UNRELATED, parser.accept("Alice2 has no known alts"));
    }
}
