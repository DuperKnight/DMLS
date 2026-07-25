package com.duperknight.client.accountlink;

import com.duperknight.DMLS;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Confirms persisted Discord links with the server and keeps connection-gated features in sync.
 */
public final class DiscordLinkSessionValidator {
    private static final Map<UUID, CompletableFuture<Boolean>> IN_FLIGHT = new ConcurrentHashMap<>();

    private DiscordLinkSessionValidator() {
    }

    public static CompletableFuture<Boolean> validateSavedLink(MinecraftClient client) {
        if (client == null || client.getSession().getUuidOrNull() == null) {
            return CompletableFuture.completedFuture(false);
        }
        UUID minecraftUuid = client.getSession().getUuidOrNull();
        String token = DiscordLinkTokenStore.load(minecraftUuid).orElse("");
        if (token.isEmpty()) {
            DiscordLinkAvailability.markUnlinked(minecraftUuid);
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> existing = IN_FLIGHT.get(minecraftUuid);
        if (existing != null) return existing;

        CompletableFuture<Boolean> created = DiscordLinkService.checkStatus(minecraftUuid, token)
                .handle((result, error) -> {
                    if (error != null || result == null) {
                        DMLS.LOGGER.debug("Discord link validation failed", error);
                        DiscordLinkAvailability.markUnlinked(minecraftUuid);
                        return false;
                    }
                    return applyResult(minecraftUuid, result);
                });
        existing = IN_FLIGHT.putIfAbsent(minecraftUuid, created);
        if (existing != null) return existing;
        created.whenComplete((linked, error) -> IN_FLIGHT.remove(minecraftUuid, created));
        return created;
    }

    private static boolean applyResult(UUID minecraftUuid, DiscordLinkService.LinkStatusResult result) {
        if (result.status() == DiscordLinkService.LinkStatus.LINKED) {
            DiscordLinkAvailability.markLinked(minecraftUuid);
            DMLSConfig.enableDoNotInstaBanByDefaultForConfirmedLink();
            if (result.profile() != null) {
                DiscordAccountProfileStore.save(result.profile());
                DiscordAvatarCache.ensureCached(result.profile());
            }
            return true;
        }

        DiscordLinkAvailability.markUnlinked(minecraftUuid);
        if (invalidatesSavedCredentials(result.status())) {
            DiscordAccountProfileStore.load(minecraftUuid).ifPresent(DiscordAvatarCache::deleteCached);
            DiscordLinkTokenStore.delete(minecraftUuid);
            DiscordAccountProfileStore.delete(minecraftUuid);
        }
        return false;
    }

    static boolean invalidatesSavedCredentials(DiscordLinkService.LinkStatus status) {
        return status == DiscordLinkService.LinkStatus.INVALID_TOKEN
                || status == DiscordLinkService.LinkStatus.EXPIRED;
    }
}
