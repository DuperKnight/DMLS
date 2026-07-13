package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivityResponseParserTest {
    @Test
    void correlatesHeaderAndHoursForExpectedPlayer() {
        var parser = new ActivityResponseParser("Alice");

        assertEquals(ActivityResponseParser.Status.PROGRESS,
                parser.parse("Activity for player Alice in the last 30 days").status());
        var complete = parser.parse("1,500 minutes or 25.5 hours");
        assertEquals(ActivityResponseParser.Status.CONFIRMED, complete.status());
        assertEquals(new ActivityResponseParser.ActivityResult("Alice", 30, 25.5), complete.result().orElseThrow());
    }

    @Test
    void ignoresWrongPlayerAndHoursBeforeHeader() {
        var parser = new ActivityResponseParser("Alice");

        assertEquals(ActivityResponseParser.Status.UNRELATED,
                parser.parse("1,500 minutes or 25.5 hours").status());
        assertEquals(ActivityResponseParser.Status.UNRELATED,
                parser.parse("Activity for player Bob in the last 30 days").status());
        assertEquals(ActivityResponseParser.Status.UNRELATED,
                parser.parse("1,500 minutes or 25.5 hours").status());
    }

    @Test
    void malformedNumericFieldsNeverThrow() {
        var parser = new ActivityResponseParser("Alice");
        assertEquals(ActivityResponseParser.Status.MALFORMED,
                parser.parse("Activity for player Alice in the last 999999999999999999999 days").status());
    }

    @Test
    void rejectsInvalidExpectedUsername() {
        assertThrows(IllegalArgumentException.class, () -> new ActivityResponseParser("not valid"));
    }
}
