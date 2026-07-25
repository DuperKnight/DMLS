package com.duperknight.client.accountlink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLinkSessionValidatorTest {
    @Test
    void onlyDefinitiveCredentialFailuresDeleteTheSavedLink() {
        assertTrue(DiscordLinkSessionValidator.invalidatesSavedCredentials(
                DiscordLinkService.LinkStatus.INVALID_TOKEN));
        assertTrue(DiscordLinkSessionValidator.invalidatesSavedCredentials(
                DiscordLinkService.LinkStatus.EXPIRED));

        assertFalse(DiscordLinkSessionValidator.invalidatesSavedCredentials(
                DiscordLinkService.LinkStatus.AUTHORIZATION_STALE));
        assertFalse(DiscordLinkSessionValidator.invalidatesSavedCredentials(
                DiscordLinkService.LinkStatus.NETWORK_ERROR));
        assertFalse(DiscordLinkSessionValidator.invalidatesSavedCredentials(
                DiscordLinkService.LinkStatus.SERVICE_ERROR));
    }
}
