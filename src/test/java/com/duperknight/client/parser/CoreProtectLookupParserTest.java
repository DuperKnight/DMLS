package com.duperknight.client.parser;

import com.duperknight.client.session.ResponseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreProtectLookupParserTest {
    @Test
    void parsesContainerEntriesAndPageMarker() {
        var parser = new CoreProtectLookupParser(CoreProtectLookupParser.ScanKind.CONTAINER);
        var parsed = parser.parse("Alice removed x12 minecraft:diamond page 2/9");

        assertEquals(ResponseStatus.PROGRESS, parsed.status());
        assertEquals(new CoreProtectLookupParser.Entry("Alice", "removed", 12, "diamond"),
                parsed.value().orElseThrow().get(0));
        assertEquals(new CoreProtectLookupParser.Page(2, 9), parsed.value().orElseThrow().get(1));
    }

    @Test
    void parsesBlockDefaultCountAndRejectsWrongMode() {
        var block = new CoreProtectLookupParser(CoreProtectLookupParser.ScanKind.BLOCK);
        assertEquals(new CoreProtectLookupParser.Entry("Bob", "placed", 1, "stone"),
                block.parse("Bob placed minecraft:stone").value().orElseThrow().get(0));

        var container = new CoreProtectLookupParser(CoreProtectLookupParser.ScanKind.CONTAINER);
        assertEquals(ResponseStatus.UNRELATED, container.parse("Bob placed minecraft:stone").status());
    }

    @Test
    void handlesNoResultsErrorsAndMalformedNumbersWithoutThrowing() {
        var parser = new CoreProtectLookupParser(CoreProtectLookupParser.ScanKind.CONTAINER);
        assertEquals(ResponseStatus.CONFIRMED, parser.parse("No lookup results.").status());
        assertEquals(ResponseStatus.REJECTED, parser.parse("[CoreProtect] No permission.").status());
        assertEquals(ResponseStatus.REJECTED,
                parser.parse("Alice removed x999999999999999999999 diamond").status());
        assertEquals(ResponseStatus.REJECTED, parser.parse("page 5/2").status());
    }

    @Test
    void ignoresUnrelatedLookalikes() {
        var parser = new CoreProtectLookupParser(CoreProtectLookupParser.ScanKind.BLOCK);
        assertEquals(ResponseStatus.UNRELATED, parser.parse("Alice says page 2 of 3").status());
        assertEquals(ResponseStatus.UNRELATED, parser.parse("rollback complete").status());
    }
}
