package com.duperknight.client.modules.session;

import com.duperknight.client.parser.CoreProtectLookupParser;
import com.duperknight.client.session.ParsedResponse;
import com.duperknight.client.session.ResponseStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure state machine for a paged CoreProtect lookup.
 *
 * <p>Entries are buffered until a page marker matches the page DMLS requested. This prevents a
 * late marker from a previous lookup from advancing or committing the current page.</p>
 */
public final class CoreProtectScanSession {
    public static final int MAX_PAGES = 1_000;
    public static final int MAX_DURATION_TICKS = 20 * 60 * 20;
    public static final int MAX_AGGREGATES = 10_000;
    public static final int PAGE_TIMEOUT_TICKS = 20 * 10;
    public static final int UNMARKED_SILENCE_TICKS = 20 * 3;
    public static final int PAGE_GAP_TICKS = 20;

    private final CoreProtectLookupParser parser;
    private final String target;
    private final Map<AggregateKey, Long> confirmed = new LinkedHashMap<>();
    private final Map<AggregateKey, Long> pending = new LinkedHashMap<>();
    private final Set<AggregateKey> distinctKeys = new HashSet<>();
    private final Map<String, String> playerDisplayNames = new LinkedHashMap<>();

    private Phase phase = Phase.AWAITING_PAGE;
    private Completion completion = Completion.ACTIVE;
    private int requestedPage = 1;
    private int confirmedPages;
    private int totalPages = 1;
    private int phaseTicks;
    private int elapsedTicks;
    private boolean unconfirmedData;

    public CoreProtectScanSession(CoreProtectLookupParser.ScanKind kind, String target) {
        this.parser = new CoreProtectLookupParser(Objects.requireNonNull(kind, "kind"));
        this.target = Objects.requireNonNull(target, "target");
    }

    /** Consumes one server line and returns any lifecycle action it caused. */
    public Action accept(String line) {
        if (phase != Phase.AWAITING_PAGE) {
            return Action.none();
        }

        ParsedResponse<List<CoreProtectLookupParser.LookupEvent>> parsed = parser.parse(line);
        if (parsed.status() == ResponseStatus.UNRELATED) {
            return Action.none();
        }
        List<CoreProtectLookupParser.LookupEvent> events = parsed.value().orElse(List.of());
        if (parsed.status() == ResponseStatus.REJECTED) {
            boolean malformed = events.stream().anyMatch(CoreProtectLookupParser.Malformed.class::isInstance);
            return stop(malformed ? Completion.MALFORMED : Completion.REJECTED, true);
        }
        if (parsed.status() == ResponseStatus.CONFIRMED) {
            boolean noResults = events.stream().anyMatch(CoreProtectLookupParser.NoResults.class::isInstance);
            if (noResults && requestedPage == 1 && confirmedPages == 0 && confirmed.isEmpty() && pending.isEmpty()) {
                confirmedPages = 1;
                totalPages = 1;
                return stop(Completion.NO_RESULTS, false);
            }
            return stop(Completion.REJECTED, true);
        }

        CoreProtectLookupParser.Page marker = events.stream()
                .filter(CoreProtectLookupParser.Page.class::isInstance)
                .map(CoreProtectLookupParser.Page.class::cast)
                .findFirst()
                .orElse(null);

        if (marker != null && marker.current() != requestedPage) {
            // Correlation is uncertain. Drop only the unconfirmed page and keep
            // every previously confirmed result.
            discardPending();
            return Action.none();
        }

        boolean relevantEntrySeen = false;
        for (CoreProtectLookupParser.LookupEvent event : events) {
            if (!(event instanceof CoreProtectLookupParser.Entry entry) || !matchesTarget(entry.player())) {
                continue;
            }
            relevantEntrySeen = true;
            AddResult added = addPending(entry);
            if (added == AddResult.AGGREGATE_LIMIT) {
                return stop(Completion.AGGREGATE_LIMIT, true);
            }
            if (added == AddResult.OVERFLOW) {
                return stop(Completion.MALFORMED, true);
            }
        }
        if (relevantEntrySeen) {
            phaseTicks = 0;
        }

        if (marker == null) {
            return Action.none();
        }

        totalPages = Math.max(totalPages, marker.total());
        if (!commitPending()) {
            return stop(Completion.MALFORMED, false);
        }
        confirmedPages = requestedPage;
        phaseTicks = 0;

        int lastPermittedPage = Math.min(totalPages, MAX_PAGES);
        if (requestedPage >= lastPermittedPage) {
            return stop(totalPages > MAX_PAGES ? Completion.PAGE_LIMIT : Completion.COMPLETE, false);
        }

        phase = Phase.PAGE_GAP;
        return Action.none();
    }

    /** Advances timers by one client tick. */
    public Action tick() {
        if (phase == Phase.FINISHED || phase == Phase.REQUEST_READY) {
            return Action.none();
        }

        elapsedTicks++;
        phaseTicks++;
        if (elapsedTicks >= MAX_DURATION_TICKS) {
            return stop(Completion.TIME_LIMIT, true);
        }

        if (phase == Phase.PAGE_GAP) {
            if (phaseTicks >= PAGE_GAP_TICKS) {
                phase = Phase.REQUEST_READY;
                return Action.requestPage(requestedPage + 1);
            }
            return Action.none();
        }

        if (!pending.isEmpty() && phaseTicks >= UNMARKED_SILENCE_TICKS) {
            return stop(Completion.UNCONFIRMED_PAGE, true);
        }
        if (phaseTicks >= PAGE_TIMEOUT_TICKS) {
            return stop(Completion.TIMEOUT, true);
        }
        return Action.none();
    }

    /** Records a successful dispatch of the page requested by {@link ActionType#REQUEST_PAGE}. */
    public void pageRequested(int page) {
        if (phase != Phase.REQUEST_READY || page != requestedPage + 1 || page > MAX_PAGES) {
            throw new IllegalStateException("Unexpected page dispatch: " + page);
        }
        requestedPage = page;
        phase = Phase.AWAITING_PAGE;
        phaseTicks = 0;
        pending.clear();
    }

    /** Stops the scan while preserving any parsed but not page-confirmed aggregates. */
    public Action stop(Completion reason) {
        return stop(reason, true);
    }

    public Snapshot snapshot() {
        List<Aggregate> aggregates = new ArrayList<>(confirmed.size());
        confirmed.forEach((key, count) -> aggregates.add(new Aggregate(
                playerDisplayNames.getOrDefault(key.player(), key.player()),
                key.action(), key.material(), count)));
        return new Snapshot(List.copyOf(aggregates), requestedPage, confirmedPages, totalPages,
                distinctKeys.size(), elapsedTicks, completion, unconfirmedData);
    }

    private Action stop(Completion reason, boolean preservePending) {
        if (phase == Phase.FINISHED) {
            return Action.none();
        }
        if (reason == Completion.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE is not a terminal completion");
        }
        if (preservePending && !pending.isEmpty()) {
            unconfirmedData = true;
            if (!commitPending()) {
                discardPending();
                reason = Completion.MALFORMED;
            }
        } else if (!preservePending) {
            discardPending();
        }
        completion = reason;
        phase = Phase.FINISHED;
        return Action.finish(reason);
    }

    private AddResult addPending(CoreProtectLookupParser.Entry entry) {
        String normalizedPlayer = entry.player().toLowerCase(Locale.ROOT);
        AggregateKey key = new AggregateKey(normalizedPlayer, entry.action(), entry.material());
        if (!distinctKeys.contains(key)) {
            if (distinctKeys.size() >= MAX_AGGREGATES) {
                return AddResult.AGGREGATE_LIMIT;
            }
            distinctKeys.add(key);
            playerDisplayNames.putIfAbsent(normalizedPlayer, entry.player());
        }

        long previous = pending.getOrDefault(key, 0L);
        try {
            pending.put(key, Math.addExact(previous, entry.count()));
            return AddResult.ADDED;
        } catch (ArithmeticException exception) {
            return AddResult.OVERFLOW;
        }
    }

    private boolean commitPending() {
        try {
            // Validate every sum before mutating the confirmed snapshot.
            for (Map.Entry<AggregateKey, Long> entry : pending.entrySet()) {
                Math.addExact(confirmed.getOrDefault(entry.getKey(), 0L), entry.getValue());
            }
            pending.forEach((key, value) -> confirmed.put(key, confirmed.getOrDefault(key, 0L) + value));
            pending.clear();
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private void discardPending() {
        pending.clear();
        distinctKeys.clear();
        distinctKeys.addAll(confirmed.keySet());
    }

    private boolean matchesTarget(String player) {
        return target.equals("*") || target.equalsIgnoreCase(player);
    }

    private enum Phase { AWAITING_PAGE, PAGE_GAP, REQUEST_READY, FINISHED }

    private enum AddResult { ADDED, AGGREGATE_LIMIT, OVERFLOW }

    private record AggregateKey(String player, String action, String material) {
    }

    public enum Completion {
        ACTIVE,
        COMPLETE,
        NO_RESULTS,
        UNCONFIRMED_PAGE,
        TIMEOUT,
        REJECTED,
        MALFORMED,
        PAGE_LIMIT,
        TIME_LIMIT,
        AGGREGATE_LIMIT,
        DISPATCH_BLOCKED,
        CANCELLED,
        CONNECTION_CHANGED,
        INTERNAL_ERROR,
        SIMULATED
    }

    public enum ActionType { NONE, REQUEST_PAGE, FINISH }

    public record Action(ActionType type, int page, Completion completion) {
        private static Action none() {
            return new Action(ActionType.NONE, 0, Completion.ACTIVE);
        }

        private static Action requestPage(int page) {
            return new Action(ActionType.REQUEST_PAGE, page, Completion.ACTIVE);
        }

        private static Action finish(Completion completion) {
            return new Action(ActionType.FINISH, 0, completion);
        }
    }

    public record Aggregate(String player, String action, String material, long count) {
    }

    public record Snapshot(
            List<Aggregate> aggregates,
            int requestedPage,
            int confirmedPages,
            int totalPages,
            int distinctAggregates,
            int elapsedTicks,
            Completion completion,
            boolean hasUnconfirmedData
    ) {
    }
}
