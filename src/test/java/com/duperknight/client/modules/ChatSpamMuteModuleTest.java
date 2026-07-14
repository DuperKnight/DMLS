package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSpamMuteModuleTest {
    @Test
    void eachToggleOnlyFiltersItsExactMessagePrefix() {
        assertTrue(ChatSpamMuteModule.shouldHide(
                "[T] Selling stone", StaffRank.ADMIN, true, false));
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[Server: Restart soon]", StaffRank.ADMIN, true, false));

        assertTrue(ChatSpamMuteModule.shouldHide(
                "[Server: Restart soon]", StaffRank.ADMIN, false, true));
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[T] Selling stone", StaffRank.ADMIN, false, true));
    }

    @Test
    void lowerRanksCannotEnableTheAdminOnlyFilter() {
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[T] Selling stone", StaffRank.SUPPORT, true, true));
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[Server: Restart soon]", StaffRank.HELPER, true, true));
    }

    @Test
    void ordinaryAndSimilarLookingMessagesRemainVisible() {
        assertFalse(ChatSpamMuteModule.shouldHide(
                "ordinary chat", StaffRank.ADMIN, true, true));
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[t] lowercase", StaffRank.ADMIN, true, true));
        assertFalse(ChatSpamMuteModule.shouldHide(
                "[Server] different delimiter", StaffRank.ADMIN, true, true));
        assertFalse(ChatSpamMuteModule.shouldHide(
                null, StaffRank.ADMIN, true, true));
    }
}
