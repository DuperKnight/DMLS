package com.duperknight.client.modules;

import com.duperknight.client.moderation.PunishmentRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentHelperModuleTest {
    @Test
    void actionChoicesGenerateActionSpecificCommands() {
        assertCommand(PunishmentHelperModule.PunishmentOption.WARNING, "ignored",
                "warn Player_1 Rule 1.2");
        assertCommand(PunishmentHelperModule.PunishmentOption.MUTE, "1w",
                "mute Player_1 1w Rule 1.2");
        assertCommand(PunishmentHelperModule.PunishmentOption.BAN, "1d",
                "ban Player_1 1d Rule 1.2");
    }

    @Test
    void blankDurationMeansPermanentAndLogsUseReadableUnits() {
        assertCommand(PunishmentHelperModule.PunishmentOption.BAN, "",
                "ban Player_1 Rule 1.2");
        assertCommand(PunishmentHelperModule.PunishmentOption.MUTE, "  ",
                "mute Player_1 Rule 1.2");

        assertEquals("4 week ban", PunishmentHelperModule.readablePunishment(
                PunishmentHelperModule.PunishmentOption.BAN, "4w"));
        assertEquals("30 minute mute", PunishmentHelperModule.readablePunishment(
                PunishmentHelperModule.PunishmentOption.MUTE, "30m"));
        assertEquals("permanent ban", PunishmentHelperModule.readablePunishment(
                PunishmentHelperModule.PunishmentOption.BAN, ""));
        assertEquals("warning", PunishmentHelperModule.readablePunishment(
                PunishmentHelperModule.PunishmentOption.WARNING, "4w"));
    }

    private static void assertCommand(PunishmentHelperModule.PunishmentOption option,
                                      String duration, String expected) {
        PunishmentHelperModule.PunishmentPreparation prepared =
                PunishmentHelperModule.preparePunishment("Player_1", option, duration, "Rule 1.2");
        assertTrue(prepared.isValid());
        PunishmentRequest request = prepared.request();
        assertEquals(expected, request.command());
    }
}
