package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatSpamFilterPolicyTest {
    @Test
    void offNeverSuppressesTradeOrServerMessages() {
        assertFalse(ChatSpamFilterPolicy.shouldHide("[T] sale", false, false));
        assertFalse(ChatSpamFilterPolicy.shouldHide("[Server: restart]", false, false));
    }

    @Test
    void eachPreferenceSuppressesOnlyItsOwnKind() {
        assertTrue(ChatSpamFilterPolicy.shouldHide("[T] sale", true, false));
        assertFalse(ChatSpamFilterPolicy.shouldHide("[Server: restart]", true, false));
        assertTrue(ChatSpamFilterPolicy.shouldHide("[Server: restart]", false, true));
        assertFalse(ChatSpamFilterPolicy.shouldHide("[T] sale", false, true));
    }
}
