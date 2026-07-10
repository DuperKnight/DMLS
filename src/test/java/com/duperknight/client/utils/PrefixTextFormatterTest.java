package com.duperknight.client.utils;

import com.duperknight.client.modules.PrefixCreateModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixTextFormatterTest {
    @Test
    void normalizesVanillaAndRgbFormatting() {
        assertEquals("<green>Hello <bold>world<reset>", PrefixTextFormatter.normalizeLegacyFormatting("&aHello &lworld&r"));
        assertEquals("<#12aBcD>Prefix", PrefixTextFormatter.normalizeLegacyFormatting("&#12aBcDPrefix"));
    }

    @Test
    void preservesMiniMessageAndRejectsMalformedHex() {
        assertEquals("<gradient:red:blue>Staff</gradient>",
                PrefixTextFormatter.normalizeLegacyFormatting("<gradient:red:blue>Staff</gradient>"));
        assertThrows(IllegalArgumentException.class,
                () -> PrefixTextFormatter.normalizeLegacyFormatting("&#GG0000Broken"));
    }

    @Test
    void resolvesLimitsAndChecksCommandBoundaries() {
        assertEquals("2147483647", PrefixCreateModule.resolveLimit("2147483647").orElseThrow());
        assertTrue(PrefixCreateModule.resolveLimit("1").isPresent());
        assertTrue(PrefixCreateModule.resolveLimit("0").isEmpty());
        assertTrue(PrefixCreateModule.resolveLimit("2147483648").isEmpty());

        int prefixTextLengthAtLimit = PrefixCreateModule.MAX_COMMAND_LENGTH - PrefixCreateModule.createCommand("id", "").length();
        assertEquals(PrefixCreateModule.MAX_COMMAND_LENGTH,
                PrefixCreateModule.createCommand("id", "x".repeat(prefixTextLengthAtLimit)).length());
        assertEquals(PrefixCreateModule.MAX_COMMAND_LENGTH + 1,
                PrefixCreateModule.createCommand("id", "x".repeat(prefixTextLengthAtLimit + 1)).length());
    }
}
