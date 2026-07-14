package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalChatMessengerTest {
    @Test
    void parsesAndClassifiesChannelListRows() {
        assertEquals(GlobalChatMessenger.LineKind.HEADER,
                GlobalChatMessenger.parseChannelListLine("Channel list:").orElseThrow().kind());
        assertEquals(new GlobalChatMessenger.ChannelListLine(
                        GlobalChatMessenger.LineKind.TRANSMITTING, "AdminChat"),
                GlobalChatMessenger.parseChannelListLine("AdminChat Status: Transmitting").orElseThrow());
        assertEquals(new GlobalChatMessenger.ChannelListLine(
                        GlobalChatMessenger.LineKind.RECEIVING, "TradeChat"),
                GlobalChatMessenger.parseChannelListLine("TradeChat Status: Receiving").orElseThrow());
        assertTrue(GlobalChatMessenger.parseChannelListLine("Welcome to the server").isEmpty());
    }

    @Test
    void recognizesExplicitGlobalChannelAliases() {
        assertTrue(GlobalChatMessenger.isPublicChannel(null));
        assertTrue(GlobalChatMessenger.isPublicChannel("public"));
        assertTrue(GlobalChatMessenger.isPublicChannel("GlobalChat"));
        assertTrue(GlobalChatMessenger.isPublicChannel("global_chat"));
        assertFalse(GlobalChatMessenger.isPublicChannel("StaffChat2"));
    }
}
