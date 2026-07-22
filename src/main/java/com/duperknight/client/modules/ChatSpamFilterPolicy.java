package com.duperknight.client.modules;

import java.util.regex.Pattern;

/** Live, side-effect-free policy for the optional vanilla-chat spam filters. */
final class ChatSpamFilterPolicy {
    private static final Pattern SERVER_SUMMON = Pattern.compile(
            "^\\[(.+): Summoned new (.+)]$");

    private ChatSpamFilterPolicy() {
    }

    static boolean shouldHide(String cleanMessage, boolean tradeChatMuted, boolean serverMessagesMuted,
                              boolean serverSummonMessagesMuted) {
        if (cleanMessage == null || cleanMessage.isEmpty()) return false;
        if (tradeChatMuted && cleanMessage.startsWith("[T]")) return true;
        if (serverMessagesMuted && cleanMessage.startsWith("[Server: ")) return true;
        return serverSummonMessagesMuted && SERVER_SUMMON.matcher(cleanMessage).matches();
    }
}
