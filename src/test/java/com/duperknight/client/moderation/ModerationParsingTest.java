package com.duperknight.client.moderation;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModerationParsingTest {
    @AfterEach
    void reset() {
        ModerationChatService.resetForTests();
    }

    @Test
    void classifiesExactChannelPrefixes() {
        assertEquals(ChatChannel.LOCAL, ChatChannel.classifyPlayerLine("[L] [VIP] | Nick : hello"));
        assertEquals(ChatChannel.TRADE, ChatChannel.classifyPlayerLine("[T] [VIP] | Nick : sale"));
        assertEquals(ChatChannel.RP, ChatChannel.classifyPlayerLine("[RP] [VIP] | Nick : waves"));
        assertEquals(ChatChannel.STAFF, ChatChannel.classifyPlayerLine("[SC] [Mod] | Nick : note"));
        assertEquals(ChatChannel.ADMIN, ChatChannel.classifyPlayerLine("[AC] [Admin] | Nick : note"));
        assertEquals(ChatChannel.GLOBAL, ChatChannel.classifyPlayerLine("[VIP] | Nick : hello"));
        assertEquals(ChatChannel.GLOBAL, ChatChannel.classifyPlayerLine(" [T] [VIP] | Nick : not exact"));
        assertEquals("g", ChatChannel.GLOBAL.sendCommand());
        assertEquals("local", ChatChannel.LOCAL.sendCommand());
        assertEquals("tradec", ChatChannel.TRADE.sendCommand());
        assertEquals("rpchat", ChatChannel.RP.sendCommand());
        assertEquals("staffc", ChatChannel.STAFF.sendCommand());
        assertEquals("adminc", ChatChannel.ADMIN.sendCommand());
        assertFalse(ChatChannel.selectableFor(true).contains(ChatChannel.GLOBAL));
        assertFalse(ChatChannel.selectableFor(false).contains(ChatChannel.ADMIN));
        assertTrue(ChatChannel.selectableFor(true).contains(ChatChannel.ADMIN));
    }

    @Test
    void parsesOnlyPlayerShapedLinesAndKeepsBody() {
        var parsed = ModerationChatService.parsePlayerLine("[T] [VIP] | Nick_1 : body: with colon").orElseThrow();
        assertEquals("Nick_1", parsed.visibleUsername());
        assertEquals("body: with colon", parsed.messageBody());
        var nickname = ModerationChatService.parsePlayerLine(
                "[T] Tinnie Enthusiast | Tr♡v_ » ✦ Selling everything").orElseThrow();
        assertEquals("Tr♡v_", nickname.visibleUsername());
        assertEquals("✦ Selling everything", nickname.messageBody());
        var staff = ModerationChatService.parsePlayerLine("[SC] [Abex] DuperKnight: alr thx").orElseThrow();
        assertEquals("DuperKnight", staff.visibleUsername());
        assertEquals("alr thx", staff.messageBody());
        var admin = ModerationChatService.parsePlayerLine("[AC] [Abex] DuperKnight: message").orElseThrow();
        assertEquals("DuperKnight", admin.visibleUsername());
        assertEquals("message", admin.messageBody());
        assertTrue(ModerationChatService.parsePlayerLine("[Server: restarting]").isEmpty());
        assertTrue(ModerationChatService.parsePlayerLine("[VIP] | invalid name : hello").isEmpty());
    }

    @Test
    void capturesChevronChannelMessagesInTheirDedicatedFeed() {
        ModerationChatService.capture(Text.literal("[L] Sr. Duper | NorwayKnight » hi"), null, false, false);
        ModerationChatService.capture(Text.literal(
                "[T] Tinnie Enthusiast | Tr♡v_ » ✦ Selling everything"), null, false, false);
        ModerationChatService.capture(Text.literal("[SC] [Abex] DuperKnight: staff message"), null, false, false);
        ModerationChatService.capture(Text.literal("[AC] [Abex] DuperKnight: admin message"), null, false, false);

        assertEquals(ChatChannel.LOCAL, ModerationChatService.messages().get(0).channel());
        assertEquals(ChatChannel.TRADE, ModerationChatService.messages().get(1).channel());
        assertEquals(ChatChannel.STAFF, ModerationChatService.messages().get(2).channel());
        assertEquals(ChatChannel.ADMIN, ModerationChatService.messages().get(3).channel());
        assertTrue(ModerationChatService.messages().stream().allMatch(ModerationMessage::playerMessage));
    }

    @Test
    void extractsRealIgnFromClickablePrefix() {
        Text line = Text.literal("[VIP]").styled(style -> style.withClickEvent(
                        new ClickEvent.SuggestCommand("/msg Real_Name ")))
                .append(Text.literal(" | Nick : hello"));
        assertEquals("Real_Name", ModerationChatService.extractIgnFromClickMetadata(line).orElseThrow());
    }

    @Test
    void acceptsOnlyCorrelatedRealnameResponses() {
        assertEquals("Real_Name", ModerationChatService.parseRealnameResponse(
                "Nick", "[Nick] is [Real_Name]").orElseThrow());
        assertTrue(ModerationChatService.parseRealnameResponse("Other", "[Nick] is [Real_Name]").isEmpty());
        assertTrue(ModerationChatService.parseRealnameResponse("Nick", "Nick is Real_Name").isEmpty());
    }

    @Test
    void captureIsBoundedAndSystemLinesStayNonInteractive() {
        for (int index = 0; index < ModerationChatService.MAX_MESSAGES + 5; index++) {
            ModerationChatService.capture(Text.literal("server line " + index), null, false, false);
        }
        assertEquals(ModerationChatService.MAX_MESSAGES, ModerationChatService.messages().size());
        ModerationMessage first = ModerationChatService.messages().getFirst();
        assertFalse(first.playerMessage());
        assertEquals(ChatChannel.SERVER, first.channel());
    }
}
