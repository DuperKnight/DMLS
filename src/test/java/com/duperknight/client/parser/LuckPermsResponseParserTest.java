package com.duperknight.client.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuckPermsResponseParserTest {
    @Test void matchesSuppliedStoneworksLinesExactly() {
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parsePermissionAlreadySet("DuperKnight", "redischat.admin",
                        "[LP] DuperKnight already has redischat.admin set in context global."));
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "DuperKnight", "helper",
                        "[LP] DuperKnight now inherits permissions from helper in context global."));
        assertEquals(LuckPermsResponseParser.Result.CONFIRMED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.REMOVE, "DuperKnight", "helper",
                        "[LP] DuperKnight no longer inherits permissions from helper in context global."));
        assertEquals(LuckPermsResponseParser.Result.UNRELATED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "Other", "helper",
                        "[LP] DuperKnight now inherits permissions from helper in context global."));
        assertEquals(LuckPermsResponseParser.Result.REJECTED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "DuperKnight", "helper",
                        "[LP] User DuperKnight could not be found."));
        assertEquals(LuckPermsResponseParser.Result.UNRELATED,
                LuckPermsResponseParser.parseParentChange(LuckPermsResponseParser.Action.ADD, "DuperKnight", "helper",
                        "[LP] User Other could not be found."));
    }

    @Test void classifiesCorrelatedPermissionOutcomesFailClosed() {
        String permission = "mcpets.elitemountvol6whitegriffon";
        assertEquals(LuckPermsResponseParser.PermissionResult.CONFIRMED,
                LuckPermsResponseParser.parsePermissionSet("Alice", permission,
                        "[LP] Set " + permission + " to true for Alice in context global."));
        assertEquals(LuckPermsResponseParser.PermissionResult.CONFIRMED,
                LuckPermsResponseParser.parsePermissionSet("Alice", permission,
                        "[LP] Alice already has " + permission + " set in context global."));
        assertEquals(LuckPermsResponseParser.PermissionResult.REJECTED,
                LuckPermsResponseParser.parsePermissionSet("Alice", permission,
                        "[LP] User Alice could not be found."));
        assertEquals(LuckPermsResponseParser.PermissionResult.UNRELATED,
                LuckPermsResponseParser.parsePermissionSet("Alice", permission,
                        "[LP] Set " + permission + " to true for Bob in context global."));
        assertEquals(LuckPermsResponseParser.PermissionResult.UNRELATED,
                LuckPermsResponseParser.parsePermissionSet("Alice", permission,
                        "Permission updated successfully."));
    }
}
