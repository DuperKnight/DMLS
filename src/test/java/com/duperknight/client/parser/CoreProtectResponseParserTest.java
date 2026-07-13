package com.duperknight.client.parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class CoreProtectResponseParserTest {
    @Test
    void acceptsOnlyNarrowCompletionLines() {
        assertEquals(CoreProtectResponseParser.Result.CONFIRMED,
                CoreProtectResponseParser.parse("[CoreProtect] Rollback complete."));
        assertEquals(CoreProtectResponseParser.Result.UNRELATED,
                CoreProtectResponseParser.parse("Player says rollback complete please"));
        assertEquals(CoreProtectResponseParser.Result.UNRELATED,
                CoreProtectResponseParser.parse("rollback complete for someone else"));
    }

    @Test
    void completionMustMatchTheExpectedMutation() {
        assertEquals(CoreProtectResponseParser.Result.CONFIRMED,
                CoreProtectResponseParser.parse("restore", "[CoreProtect] Restore complete."));
        assertEquals(CoreProtectResponseParser.Result.UNRELATED,
                CoreProtectResponseParser.parse("rollback", "[CoreProtect] Restore complete."));
        assertEquals(CoreProtectResponseParser.Result.REJECTED,
                CoreProtectResponseParser.parse("restore", "[CoreProtect] Restore failed: no changes"));
    }
}
