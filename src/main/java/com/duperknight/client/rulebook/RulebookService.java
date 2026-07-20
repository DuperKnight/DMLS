package com.duperknight.client.rulebook;

import com.duperknight.DMLS;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the anonymous Google Docs fetch, validated cache and immutable live document snapshot. */
public final class RulebookService {
    public static final URI EXPORT_URI = URI.create(
            "https://docs.google.com/document/d/1raHKuMt59czFlqpvvBPZWHpoagiPiNOiNy9FKO70Olo/export?format=html");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Source source;
    private final RulebookCache cache;
    private final Clock clock;
    private final ExecutorService executor;
    private volatile RulebookSnapshot snapshot = new RulebookSnapshot(
            RulebookStatus.LOADING, null, null, "", false, 0);
    private CompletableFuture<RulebookSnapshot> activeRefresh;
    private boolean registered;
    private boolean bootstrapped;

    RulebookService(Source source, RulebookCache cache, Clock clock, ExecutorService executor) {
        this.source = source;
        this.cache = cache;
        this.clock = clock;
        this.executor = executor;
    }

    private static RulebookService createShared() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        Path cachePath = FabricLoader.getInstance().getConfigDir().resolve("dmls-rulebook-cache.zip");
        ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DMLS rulebook worker");
            thread.setDaemon(true);
            return thread;
        });
        String version = FabricLoader.getInstance().getModContainer("dmls")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        String userAgent = "DuperKnight/DMLS/" + version;
        return new RulebookService(() -> download(client, userAgent), new RulebookCache(cachePath), Clock.systemUTC(), worker);
    }

    public static RulebookService shared() {
        return Holder.SHARED;
    }

    public synchronized void register() {
        if (registered) return;
        registered = true;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> bootstrap());
    }

    public RulebookSnapshot snapshot() {
        return snapshot;
    }

    public List<RulebookRule> search(String query) {
        RulebookDocument document = snapshot.document();
        if (document == null) return List.of();
        String needle = Objects.requireNonNullElse(query, "").trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return document.rules();
        return document.rules().stream().filter(rule -> rule.matches(needle)).toList();
    }

    public synchronized CompletableFuture<RulebookSnapshot> refresh() {
        if (activeRefresh != null && !activeRefresh.isDone()) return activeRefresh;
        RulebookSnapshot current = snapshot;
        publish(current.document() == null ? RulebookStatus.LOADING : current.status(), current.document(),
                current.fetchedAt(), "", true);
        activeRefresh = CompletableFuture.supplyAsync(this::fetch, executor)
                .exceptionally(error -> fail(error.getCause() == null ? error : error.getCause()));
        return activeRefresh;
    }

    private synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        CompletableFuture.runAsync(this::loadCache, executor).whenComplete((unused, error) -> refresh());
    }

    void loadCache() {
        try {
            RulebookCache.CacheEntry entry = cache.load();
            if (entry == null) return;
            RulebookDocument document = GoogleDocsRulebookParser.parse(entry.html());
            publish(RulebookStatus.STALE, document, entry.fetchedAt(), "", false);
        } catch (Exception exception) {
            DMLS.LOGGER.warn("Could not load the validated rulebook cache", exception);
        }
    }

    private RulebookSnapshot fetch() {
        try {
            String html = source.fetch();
            RulebookDocument document = GoogleDocsRulebookParser.parse(html);
            Instant fetchedAt = clock.instant();
            try {
                cache.store(html, fetchedAt);
            } catch (IOException cacheFailure) {
                DMLS.LOGGER.warn("Fetched the live rulebook but could not update its cache", cacheFailure);
            }
            return publish(RulebookStatus.LIVE, document, fetchedAt, "", false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fail(interrupted);
        } catch (Exception exception) {
            return fail(exception);
        }
    }

    private static String download(HttpClient httpClient, String userAgent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(EXPORT_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html")
                .header("User-Agent", userAgent)
                .GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("Google Docs returned HTTP " + response.statusCode());
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            throw new IOException("Google Docs returned an unexpected content type");
        }
        byte[] bytes;
        try (InputStream body = response.body()) {
            bytes = RulebookCache.readBounded(body, RulebookCache.MAX_HTML_BYTES);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private synchronized RulebookSnapshot fail(Throwable error) {
        RulebookSnapshot current = snapshot;
        RulebookStatus status = current.document() == null ? RulebookStatus.UNAVAILABLE
                : current.status() == RulebookStatus.LIVE ? RulebookStatus.LIVE : RulebookStatus.STALE;
        String message = error == null || error.getMessage() == null ? "Could not connect to Google Docs" : error.getMessage();
        DMLS.LOGGER.warn("Could not refresh the live Google Docs rulebook: {}", message);
        return publish(status, current.document(), current.fetchedAt(), message, false);
    }

    private synchronized RulebookSnapshot publish(RulebookStatus status, RulebookDocument document,
                                                  Instant fetchedAt, String error, boolean refreshing) {
        snapshot = new RulebookSnapshot(status, document, fetchedAt, Objects.requireNonNullElse(error, ""),
                refreshing, snapshot.revision() + 1);
        return snapshot;
    }

    @FunctionalInterface
    interface Source {
        String fetch() throws Exception;
    }

    private static final class Holder {
        private static final RulebookService SHARED = createShared();
    }
}
