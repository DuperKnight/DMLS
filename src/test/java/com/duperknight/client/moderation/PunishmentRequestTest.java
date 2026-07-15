package com.duperknight.client.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PunishmentRequestTest {
    @Test
    void composesAllExactCommandForms() {
        assertEquals("ban Player 7d griefing",
                new PunishmentRequest(PunishmentType.BAN, "Player", "7d", "griefing").command());
        assertEquals("mute Player 30m spam",
                new PunishmentRequest(PunishmentType.MUTE, "Player", "30m", "spam").command());
        assertEquals("kick Player cool down",
                new PunishmentRequest(PunishmentType.KICK, "Player", "", "cool down").command());
        assertEquals("warn Player language",
                new PunishmentRequest(PunishmentType.WARNING, "Player", "", "language").command());
    }

    @Test
    void validatesDurationsOnlyWhereRequired() {
        assertEquals(PunishmentRequest.Validation.INVALID_DURATION,
                PunishmentRequest.validate(PunishmentType.BAN, "Player", "soon", "reason"));
        assertEquals(PunishmentRequest.Validation.VALID,
                PunishmentRequest.validate(PunishmentType.KICK, "Player", "soon", "reason"));
        assertEquals(PunishmentRequest.Validation.INVALID_REASON,
                PunishmentRequest.validate(PunishmentType.WARNING, "Player", "", ""));
    }

    @Test
    void banAloneRequiresModerator() {
        assertEquals(com.duperknight.client.modules.StaffRank.MODERATOR, PunishmentType.BAN.minimumRank());
        assertEquals(com.duperknight.client.modules.StaffRank.HELPER, PunishmentType.MUTE.minimumRank());
        assertEquals(com.duperknight.client.modules.StaffRank.HELPER, PunishmentType.KICK.minimumRank());
        assertEquals(com.duperknight.client.modules.StaffRank.HELPER, PunishmentType.WARNING.minimumRank());
    }
}
