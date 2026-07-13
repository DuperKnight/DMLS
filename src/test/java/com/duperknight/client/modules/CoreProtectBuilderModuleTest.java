package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreProtectBuilderModuleTest {
    @Test
    void buildsNormalizedLookupAndMarksItNonDestructive() {
        var result = CoreProtectBuilderModule.build(
                " LOOKUP ", "Player_1", " 30D ", "25", "BLOCK", "stone, diamond_ore", "tnt");

        assertTrue(result.valid());
        assertFalse(result.destructive());
        assertEquals("co lookup u:Player_1 t:30d r:25 a:block i:stone,diamond_ore e:tnt", result.command());
    }

    @Test
    void destructiveModesRequireTimeAndTarget() {
        var noTime = CoreProtectBuilderModule.build("rollback", "Player", "", "", "block", "", "");
        assertFalse(noTime.valid());
        assertEquals("dmls.validation.co.time_required", noTime.errorKey());

        var noTarget = CoreProtectBuilderModule.build("restore", "", "2h", "", "block", "", "");
        assertFalse(noTarget.valid());
        assertEquals("dmls.validation.co.target_required", noTarget.errorKey());

        var valid = CoreProtectBuilderModule.build("restore", "Player", "2h", "#global", "block", "", "");
        assertTrue(valid.valid());
        assertTrue(valid.destructive());
    }

    @Test
    void rejectsUnknownActionsAndOversizedCommands() {
        var action = CoreProtectBuilderModule.build("lookup", "Player", "", "", "made-up", "", "");
        assertFalse(action.valid());
        assertEquals("dmls.validation.co.action", action.errorKey());

        var longList = "stone,".repeat(60);
        var tooLong = CoreProtectBuilderModule.build("lookup", "Player", "", "", "block", longList, "");
        assertFalse(tooLong.valid());
        assertEquals("dmls.validation.co.command_too_long", tooLong.errorKey());
    }

    @Test
    void nullInputsDoNotThrow() {
        var result = CoreProtectBuilderModule.build(null, null, null, null, null, null, null);
        assertFalse(result.valid());
        assertEquals("dmls.validation.co.mode", result.errorKey());
    }

    @Test
    void prepareFreezesTheExactValidatedCommandForReviewAndExecution() {
        var request = CoreProtectBuilderModule.prepare(
                " RESTORE ", "Player_1", " 2H ", "#GLOBAL", "BLOCK", "stone, diamond_ore", "");

        assertTrue(request.valid());
        assertTrue(request.destructive());
        assertEquals("restore", request.mode());
        assertEquals("block", request.action());
        assertEquals("co restore u:Player_1 t:2h r:#global a:block i:stone,diamond_ore", request.command());
    }
}
