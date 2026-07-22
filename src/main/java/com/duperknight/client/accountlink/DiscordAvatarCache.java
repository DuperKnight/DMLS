package com.duperknight.client.accountlink;

import com.duperknight.DMLS;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads Discord avatars once and exposes their cached PNGs as dynamic Minecraft textures. */
public final class DiscordAvatarCache {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 1_024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<Path, CompletableFuture<Path>> IN_FLIGHT = new ConcurrentHashMap<>();

    private DiscordAvatarCache() {
    }

    public static CompletableFuture<Path> ensureCached(DiscordAccountProfile profile) {
        Path target = avatarPath(profile);
        if (Files.isRegularFile(target)) return CompletableFuture.completedFuture(target);
        return IN_FLIGHT.computeIfAbsent(target, ignored -> download(profile, target)
                .whenComplete((path, error) -> IN_FLIGHT.remove(target)));
    }

    public static Identifier loadTexture(MinecraftClient client, DiscordAccountProfile profile) {
        Path path = avatarPath(profile);
        if (!Files.isRegularFile(path)) return null;
        try (InputStream stream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(stream);
            if (!validDimensions(image)) {
                image.close();
                Files.deleteIfExists(path);
                return null;
            }
            Identifier identifier = textureIdentifier(profile);
            client.getTextureManager().registerTexture(identifier,
                    new NativeImageBackedTexture(() -> "DMLS Discord avatar", image));
            return identifier;
        } catch (IOException | RuntimeException error) {
            DMLS.LOGGER.warn("Could not load the cached Discord avatar", error);
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupError) {
                DMLS.LOGGER.debug("Could not delete the invalid Discord avatar cache", cleanupError);
            }
            return null;
        }
    }

    public static void deleteCached(DiscordAccountProfile profile) {
        try {
            Files.deleteIfExists(avatarPath(profile));
        } catch (IOException error) {
            DMLS.LOGGER.debug("Could not delete the cached Discord avatar", error);
        }
    }

    private static CompletableFuture<Path> download(DiscordAccountProfile profile, Path target) {
        HttpRequest request = HttpRequest.newBuilder(profile.avatarUri())
                .timeout(TIMEOUT)
                .header("Accept", "image/png,image/*;q=0.8")
                .header("User-Agent", "DuperKnight/DMLS")
                .GET()
                .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200 || response.body().length == 0
                            || response.body().length > MAX_IMAGE_BYTES) {
                        throw new IllegalStateException("Discord avatar request returned HTTP " + response.statusCode());
                    }
                    return writeValidatedImage(target, response.body());
                })
                .exceptionally(error -> {
                    DMLS.LOGGER.warn("Could not cache the Discord avatar", error);
                    return null;
                });
    }

    private static Path writeValidatedImage(Path target, byte[] encoded) {
        Path temporary = null;
        try (NativeImage image = NativeImage.read(encoded)) {
            if (!validDimensions(image)) throw new IOException("Discord avatar dimensions are invalid");
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "dmls-discord-avatar-", ".png");
            image.writeTo(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Could not decode the Discord avatar", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup after a failed image write.
                }
            }
        }
    }

    private static boolean validDimensions(NativeImage image) {
        return image.getWidth() > 0 && image.getHeight() > 0
                && image.getWidth() <= MAX_IMAGE_DIMENSION && image.getHeight() <= MAX_IMAGE_DIMENSION;
    }

    private static Path avatarPath(DiscordAccountProfile profile) {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls-discord-avatars")
                .resolve(profile.minecraftUuid() + "-" + avatarHash(profile.discordAvatarUrl()) + ".png");
    }

    private static Identifier textureIdentifier(DiscordAccountProfile profile) {
        return Identifier.of(DMLS.MOD_ID, "discord_avatar/" + profile.minecraftUuid() + "_"
                + avatarHash(profile.discordAvatarUrl()));
    }

    private static String avatarHash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
