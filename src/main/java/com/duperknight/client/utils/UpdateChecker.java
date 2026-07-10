package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Checks Modrinth for a newer DMLS release whenever the client joins a world. */
public final class UpdateChecker {
    private static final String PROJECT_ID = "VEzK5sbV";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final long NOTICE_DURATION_MILLIS = 15_000L;
    private static final int NOTICE_COLOR = 0xFF55FF55;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicLong JOIN_GENERATION = new AtomicLong();

    private static volatile Text notice;
    private static volatile long noticeUntilMillis;

    private UpdateChecker() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> checkForUpdate(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            JOIN_GENERATION.incrementAndGet();
            notice = null;
            noticeUntilMillis = 0L;
        });

        HudElementRegistry.addLast(Identifier.of("dmls", "update_notice"), (context, tickCounter) -> {
            Text currentNotice = notice;
            if (currentNotice == null || System.currentTimeMillis() >= noticeUntilMillis) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && !client.options.hudHidden) {
                context.drawTextWithShadow(client.textRenderer, currentNotice, 6, 6, NOTICE_COLOR);
            }
        });
    }

    private static void checkForUpdate(MinecraftClient client) {
        long generation = JOIN_GENERATION.incrementAndGet();
        notice = null;
        noticeUntilMillis = 0L;

        String currentVersion = FabricLoader.getInstance()
                .getModContainer("dmls")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
        String minecraftVersion = minecraftVersionFrom(currentVersion);
        if (minecraftVersion == null) {
            DMLS.LOGGER.warn("Cannot check for updates: DMLS version '{}' does not contain a Minecraft version", currentVersion);
            return;
        }

        URI uri = URI.create("https://api.modrinth.com/v2/project/" + PROJECT_ID
                + "/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
                + minecraftVersion + "%22%5D&include_changelog=false");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "DuperKnight/DMLS/" + currentVersion + " (github.com/DuperKnight/DMLS)")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Modrinth returned HTTP " + response.statusCode());
                    }
                    return newestVersion(response.body());
                })
                .thenAccept(latestVersion -> {
                    if (latestVersion != null && isNewer(latestVersion, currentVersion)) {
                        client.execute(() -> showNotice(client, generation, latestVersion));
                    }
                })
                .exceptionally(error -> {
                    DMLS.LOGGER.warn("Could not check Modrinth for a DMLS update", error);
                    return null;
                });
    }

    private static String newestVersion(String responseBody) {
        JsonArray versions = JsonParser.parseString(responseBody).getAsJsonArray();
        String newest = null;
        Version newestParsed = null;

        for (JsonElement element : versions) {
            JsonObject versionObject = element.getAsJsonObject();
            String candidate = versionObject.get("version_number").getAsString();
            try {
                Version parsed = Version.parse(candidate);
                if (newestParsed == null || parsed.compareTo(newestParsed) > 0) {
                    newest = candidate;
                    newestParsed = parsed;
                }
            } catch (VersionParsingException error) {
                DMLS.LOGGER.debug("Ignoring non-semantic Modrinth version '{}'", candidate);
            }
        }
        return newest;
    }

    private static boolean isNewer(String candidate, String current) {
        try {
            return Version.parse(candidate).compareTo(Version.parse(current)) > 0;
        } catch (VersionParsingException error) {
            DMLS.LOGGER.warn("Could not compare DMLS versions '{}' and '{}'", candidate, current);
            return false;
        }
    }

    private static String minecraftVersionFrom(String version) {
        int separator = version.indexOf('+');
        return separator >= 0 && separator < version.length() - 1 ? version.substring(separator + 1) : null;
    }

    private static void showNotice(MinecraftClient client, long generation, String latestVersion) {
        if (generation != JOIN_GENERATION.get() || client.world == null) {
            return;
        }

        notice = Text.translatable("dmls.update.available", latestVersion);
        noticeUntilMillis = System.currentTimeMillis() + NOTICE_DURATION_MILLIS;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.0F));
    }
}
