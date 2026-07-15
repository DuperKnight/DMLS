package com.duperknight.client.modules;

/** Live, side-effect-free policy for the optional vanilla-chat spam filters. */
final class ChatSpamFilterPolicy {
    private ChatSpamFilterPolicy() {
    }

    static boolean shouldHide(String cleanMessage, boolean tradeChatMuted, boolean serverMessagesMuted) {
        if (cleanMessage == null || cleanMessage.isEmpty()) return false;
        if (tradeChatMuted && cleanMessage.startsWith("[T]")) return true;
        return serverMessagesMuted && cleanMessage.startsWith("[Server: ");
    }
}
