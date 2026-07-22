package com.duperknight.client.accountlink;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Discord identity returned for a currently linked Minecraft account. */
public record DiscordAccountProfile(UUID minecraftUuid, String discordUserId, String discordUsername,
                                    String discordDisplayName, String discordAvatarUrl) {
    private static final Pattern DISCORD_USER_ID = Pattern.compile("[0-9]{5,30}");

    public DiscordAccountProfile {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        discordUserId = validatedText(discordUserId, 30, "discordUserId");
        if (!DISCORD_USER_ID.matcher(discordUserId).matches()) {
            throw new IllegalArgumentException("Invalid Discord user ID");
        }
        discordUsername = validatedText(discordUsername, 64, "discordUsername");
        discordDisplayName = validatedText(discordDisplayName, 128, "discordDisplayName");
        discordAvatarUrl = validatedText(discordAvatarUrl, 2_048, "discordAvatarUrl");
        URI avatarUri = URI.create(discordAvatarUrl);
        if (!"https".equalsIgnoreCase(avatarUri.getScheme())
                || !"cdn.discordapp.com".equalsIgnoreCase(avatarUri.getHost())) {
            throw new IllegalArgumentException("Unsupported Discord avatar URL");
        }
    }

    public URI avatarUri() {
        return URI.create(discordAvatarUrl);
    }

    private static String validatedText(String value, int maxLength, String field) {
        Objects.requireNonNull(value, field);
        String cleaned = value.trim();
        if (cleaned.isEmpty() || cleaned.length() > maxLength || cleaned.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return cleaned;
    }
}
