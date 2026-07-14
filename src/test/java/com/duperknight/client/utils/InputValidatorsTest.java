package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorsTest {
    @Test void validatesUsernamesDeliberatelyAtThreeToSixteenCharacters() {
        assertTrue(InputValidators.isUsername("Ab_123"));
        assertFalse(InputValidators.isUsername("ab"));
        assertFalse(InputValidators.isUsername("name-with-dash"));
        assertFalse(InputValidators.isUsername("x".repeat(17)));
    }

    @Test void validatesPrefixIdsAndRejectsCommandSyntax() {
        assertTrue(InputValidators.isPrefixId("support_2026-test"));
        assertFalse(InputValidators.isPrefixId("has space"));
        assertFalse(InputValidators.isPrefixId("semi;colon"));
        assertFalse(InputValidators.isPrefixId("line\nbreak"));
        assertFalse(InputValidators.isPrefixId("x".repeat(InputValidators.MAX_PREFIX_ID_LENGTH + 1)));
    }

    @Test void parsesUniqueNamesAndReportsRejectedValues() {
        var rejected = new ArrayList<String>();
        assertEquals(java.util.List.of("Alice", "bob"),
                InputValidators.uniqueUsernames("Alice, bob ALICE Bob", rejected));
        assertTrue(rejected.isEmpty());
    }

    @Test void validatesCommandArgumentsWithoutForbiddingSpaces() {
        assertTrue(InputValidators.isSafeCommandArgument("First Land", 64));
        assertFalse(InputValidators.isSafeCommandArgument("line\nbreak", 64));
        assertFalse(InputValidators.isSafeCommandArgument(" ", 64));
        assertFalse(InputValidators.isSafeCommandArgument("x".repeat(65), 64));
    }
}
