package com.duperknight.client.moderation;

import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;

/** One immutable line captured for the current live moderation session. */
public record ModerationMessage(
        long sequence,
        String time,
        Text text,
        String cleanText,
        ChatChannel channel,
        boolean playerMessage,
        String visibleUsername,
        String messageBody,
        String capturedIgn,
        UUID capturedUuid
) {
    public Optional<String> capturedIgnOptional() {
        return capturedIgn == null || capturedIgn.isBlank() ? Optional.empty() : Optional.of(capturedIgn);
    }

    public Optional<UUID> capturedUuidOptional() {
        return Optional.ofNullable(capturedUuid);
    }

    public ModerationMessage withText(Text replacement) {
        return new ModerationMessage(sequence, time, replacement, cleanText, channel, playerMessage,
                visibleUsername, messageBody, capturedIgn, capturedUuid);
    }
}
