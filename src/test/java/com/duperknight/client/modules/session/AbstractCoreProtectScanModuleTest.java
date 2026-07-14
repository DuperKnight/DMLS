package com.duperknight.client.modules.session;

import com.duperknight.client.parser.CoreProtectLookupParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractCoreProtectScanModuleTest {
    @Test
    void preparesExactContainerAndBlockLookupCommands() {
        var containers = AbstractCoreProtectScanModule.prepare(
                CoreProtectLookupParser.ScanKind.CONTAINER, " * ", " 7D ", " 20 ");
        assertTrue(containers.valid());
        assertEquals("*", containers.request().target());
        assertEquals("co lookup t:7d r:20 a:container", containers.request().command());

        var blocks = AbstractCoreProtectScanModule.prepare(
                CoreProtectLookupParser.ScanKind.BLOCK, "Player_1", "1H30M", "#GLOBAL");
        assertTrue(blocks.valid());
        assertEquals("co lookup u:Player_1 t:1h30m r:#global a:block", blocks.request().command());
    }

    @Test
    void rejectsEachInvalidFieldBeforeStartingAnOperation() {
        assertEquals(AbstractCoreProtectScanModule.PreparationStatus.INVALID_IGN,
                AbstractCoreProtectScanModule.prepare(
                        CoreProtectLookupParser.ScanKind.CONTAINER, "bad-name", "7d", "20").status());
        assertEquals(AbstractCoreProtectScanModule.PreparationStatus.INVALID_TIME,
                AbstractCoreProtectScanModule.prepare(
                        CoreProtectLookupParser.ScanKind.CONTAINER, "Alice", "forever", "20").status());
        assertEquals(AbstractCoreProtectScanModule.PreparationStatus.INVALID_TIME,
                AbstractCoreProtectScanModule.prepare(
                        CoreProtectLookupParser.ScanKind.CONTAINER, "Alice", "1h".repeat(9), "20").status());
        assertEquals(AbstractCoreProtectScanModule.PreparationStatus.INVALID_RADIUS,
                AbstractCoreProtectScanModule.prepare(
                        CoreProtectLookupParser.ScanKind.BLOCK, "Alice", "7d", "nearby").status());
        assertFalse(AbstractCoreProtectScanModule.prepare(
                CoreProtectLookupParser.ScanKind.BLOCK, null, null, null).valid());
    }
}
