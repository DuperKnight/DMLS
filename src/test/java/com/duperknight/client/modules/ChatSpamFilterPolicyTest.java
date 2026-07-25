package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatSpamFilterPolicyTest {
    @Test
    void recognizesPlayerCommandFeedbackWithoutValidatingTheSenderAsAnIgn() {
        assertTrue(ChatSpamFilterPolicy.isPlayerCommandFeedback(
                "[Arthur: Teleported Arthur to 10, 64, 10]"));
        assertTrue(ChatSpamFilterPolicy.isPlayerCommandFeedback(
                "[not-a-valid-ign: Command completed successfully]"));
    }

    @Test
    void requiresTheExactBracketedSenderPrefixAndNonemptyFeedback() {
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Arthur : feedback]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Art hur: feedback]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Art[hur: feedback]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Arthur:feedback]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Arthur: ]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("Arthur: feedback]"));
        assertFalse(ChatSpamFilterPolicy.isPlayerCommandFeedback("[Arthur: feedback"));
    }

    @Test
    void onlyAppliesThePatternWhenItsToggleIsMuted() {
        String feedback = "[Arthur: Command completed successfully]";

        assertTrue(ChatSpamFilterPolicy.shouldHide(feedback, false, false, true));
        assertFalse(ChatSpamFilterPolicy.shouldHide(feedback, false, false, false));
    }
}
