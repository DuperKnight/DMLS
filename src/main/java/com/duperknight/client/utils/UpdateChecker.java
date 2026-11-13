package com.duperknight.client.utils;

import com.duperknight.DMLS;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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

    private static volatile Component notice;
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

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("dmls", "update_notice"), (context, tickCounter) -> {
            Component currentNotice = notice;
            if (currentNotice == null || System.currentTimeMillis() >= noticeUntilMillis) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client.level != null && !client.gui.hud.isHidden()) {
                context.text(client.font, currentNotice, 6, 6, NOTICE_COLOR);
            }
        });
    }

    private static void checkForUpdate(Minecraft client) {
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
                    return newerVersionThan(response.body(), currentVersion);
                })
                .thenAccept(latestVersion -> {
                    if (latestVersion != null) {
                        client.execute(() -> showNotice(client, generation, latestVersion));
                    }
                })
                .exceptionally(error -> {
                    DMLS.LOGGER.warn("Could not check Modrinth for a DMLS update", error);
                    return null;
                });
    }

    /**
     * Returns the version_number of the most recently published release if it was
     * published after the currently installed version, otherwise null.
     * Ordering uses Modrinth's publish date, so version-number style doesn't matter.
     */
    private static String newerVersionThan(String responseBody, String currentVersion) {
        JsonArray versions = JsonParser.parseString(responseBody).getAsJsonArray();

        String newest = null;
        Instant newestDate = null;
        Instant currentDate = null;

        for (JsonElement element : versions) {
            JsonObject versionObject = element.getAsJsonObject();
            String number = versionObject.get("version_number").getAsString();
            Instant published;
            try {
                published = Instant.parse(versionObject.get("date_published").getAsString());
            } catch (RuntimeException error) {
                DMLS.LOGGER.debug("Ignoring Modrinth version '{}' with unreadable date", number);
                continue;
            }

            if (number.equals(currentVersion)) {
                currentDate = published;
            }
            if (newestDate == null || published.isAfter(newestDate)) {
                newest = number;
                newestDate = published;
            }
        }

        if (newest == null || currentDate == null) {
            // Nothing found, or the running version isn't on Modrinth (e.g. a dev build).
            return null;
        }
        return newestDate.isAfter(currentDate) ? newest : null;
    }

    private static String minecraftVersionFrom(String version) {
        int separator = version.indexOf('+');
        return separator >= 0 && separator < version.length() - 1 ? version.substring(separator + 1) : null;
    }

    private static void showNotice(Minecraft client, long generation, String latestVersion) {
        if (generation != JOIN_GENERATION.get() || client.level == null) {
            return;
        }

        notice = Component.translatable("dmls.update.available", latestVersion);
        noticeUntilMillis = System.currentTimeMillis() + NOTICE_DURATION_MILLIS;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0F));
    }
}