package com.duperknight.client.modules;

/** Live, side-effect-free policy for the optional vanilla-chat spam filters. */
final class ChatSpamFilterPolicy {
    private ChatSpamFilterPolicy() {
    }

    static boolean shouldHide(String cleanMessage, boolean tradeChatMuted, boolean serverMessagesMuted,
                              boolean staffCommandFeedbackMuted) {
        if (cleanMessage == null || cleanMessage.isEmpty()) return false;
        if (tradeChatMuted && cleanMessage.startsWith("[T]")) return true;
        if (serverMessagesMuted && cleanMessage.startsWith("[Server: ")) return true;
        return staffCommandFeedbackMuted && isStaffCommandFeedback(cleanMessage);
    }

    /**
     * Matches command feedback shaped like "[sender: feedback]". The sender is deliberately
     * treated as an opaque token rather than validated as a Minecraft username.
     */
    static boolean isStaffCommandFeedback(String cleanMessage) {
        if (cleanMessage == null || cleanMessage.length() < 6
                || cleanMessage.charAt(0) != '[' || cleanMessage.charAt(cleanMessage.length() - 1) != ']') {
            return false;
        }

        int separator = cleanMessage.indexOf(": ", 1);
        if (separator <= 1 || separator + 2 >= cleanMessage.length() - 1) {
            return false;
        }
        for (int index = 1; index < separator; index++) {
            char character = cleanMessage.charAt(index);
            if (Character.isWhitespace(character) || character == '[' || character == ':') {
                return false;
            }
        }
        return true;
    }
}
