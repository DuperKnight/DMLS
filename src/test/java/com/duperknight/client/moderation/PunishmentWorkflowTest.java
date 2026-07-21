package com.duperknight.client.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentWorkflowTest {
    @Test
    void actionChoicesGenerateActionSpecificCommands() {
        assertCommand(PunishmentWorkflow.PunishmentOption.WARNING, "ignored",
                "warn Player_1 Rule 1.2");
        assertCommand(PunishmentWorkflow.PunishmentOption.MUTE, "1w",
                "mute Player_1 1w Rule 1.2");
        assertCommand(PunishmentWorkflow.PunishmentOption.BAN, "1d",
                "ban Player_1 1d Rule 1.2");
    }

    @Test
    void blankDurationMeansPermanentAndLogsUseReadableUnits() {
        assertCommand(PunishmentWorkflow.PunishmentOption.BAN, "",
                "ban Player_1 Rule 1.2");
        assertCommand(PunishmentWorkflow.PunishmentOption.MUTE, "  ",
                "mute Player_1 Rule 1.2");

        assertEquals("4 week ban", PunishmentWorkflow.readablePunishment(
                PunishmentWorkflow.PunishmentOption.BAN, "4w"));
        assertEquals("30 minute mute", PunishmentWorkflow.readablePunishment(
                PunishmentWorkflow.PunishmentOption.MUTE, "30m"));
        assertEquals("permanent ban", PunishmentWorkflow.readablePunishment(
                PunishmentWorkflow.PunishmentOption.BAN, ""));
        assertEquals("warning", PunishmentWorkflow.readablePunishment(
                PunishmentWorkflow.PunishmentOption.WARNING, "4w"));
    }

    private static void assertCommand(PunishmentWorkflow.PunishmentOption option,
                                      String duration, String expected) {
        PunishmentWorkflow.PunishmentPreparation prepared =
                PunishmentWorkflow.preparePunishment("Player_1", option, duration, "Rule 1.2");
        assertTrue(prepared.isValid());
        PunishmentRequest request = prepared.request();
        assertEquals(expected, request.command());
    }
}
