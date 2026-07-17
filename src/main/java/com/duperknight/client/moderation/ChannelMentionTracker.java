package com.duperknight.client.moderation;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tracks unread username mentions for the channel selector during one server connection. */
final class ChannelMentionTracker {
    private final Set<ChatChannel> unreadChannels = EnumSet.noneOf(ChatChannel.class);
    private long lastProcessedSequence;

    void update(List<ModerationMessage> messages, String username, ChatChannel selectedChannel,
                List<ChatChannel> availableChannels) {
        if (messages.isEmpty()) {
            if (lastProcessedSequence > 0) reset();
            return;
        }

        long newestSequence = messages.getLast().sequence();
        if (newestSequence < lastProcessedSequence) reset();
        Set<ChatChannel> available = availableChannels.isEmpty()
                ? EnumSet.noneOf(ChatChannel.class)
                : EnumSet.copyOf(availableChannels);
        for (ModerationMessage message : messages) {
            if (message.sequence() <= lastProcessedSequence) continue;
            if (message.channel() != selectedChannel
                    && available.contains(message.channel())
                    && mentionsUsername(message.messageBody(), username)) {
                unreadChannels.add(message.channel());
            }
        }
        lastProcessedSequence = newestSequence;
    }

    void markRead(ChatChannel channel) {
        unreadChannels.remove(channel);
    }

    boolean hasUnread() {
        return !unreadChannels.isEmpty();
    }

    boolean isUnread(ChatChannel channel) {
        return unreadChannels.contains(channel);
    }

    void reset() {
        unreadChannels.clear();
        lastProcessedSequence = 0;
    }

    static boolean mentionsUsername(String message, String username) {
        if (message == null || username == null || username.isBlank()) return false;
        String haystack = message.toLowerCase(Locale.ROOT);
        String needle = username.toLowerCase(Locale.ROOT);
        int fromIndex = 0;
        while (fromIndex <= haystack.length() - needle.length()) {
            int match = haystack.indexOf(needle, fromIndex);
            if (match < 0) return false;
            int end = match + needle.length();
            boolean boundedLeft = match == 0 || !isUsernameCharacter(haystack.charAt(match - 1));
            boolean boundedRight = end == haystack.length() || !isUsernameCharacter(haystack.charAt(end));
            if (boundedLeft && boundedRight) return true;
            fromIndex = match + 1;
        }
        return false;
    }

    private static boolean isUsernameCharacter(char character) {
        return character == '_' || character >= '0' && character <= '9'
                || character >= 'a' && character <= 'z';
    }
}
