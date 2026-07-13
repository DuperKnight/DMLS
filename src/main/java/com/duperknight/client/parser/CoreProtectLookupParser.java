package com.duperknight.client.parser;

import com.duperknight.client.session.ParsedResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, typed parser for the CoreProtect lookup lines consumed by DMLS scans. */
public final class CoreProtectLookupParser {
    private static final Pattern CONTAINER_ENTRY = Pattern.compile(
            "([A-Za-z0-9_]{3,16}) (removed|added) x?(\\d+) (?:minecraft:)?([a-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_ENTRY = Pattern.compile(
            "([A-Za-z0-9_]{3,16}) (broke|placed) (?:x(\\d+) )?(?:minecraft:)?([a-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("(?:^|\\s)page (\\d+)/(\\d+)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_RESULTS = Pattern.compile("^.*no (?:lookup )?results[.!]?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REJECTED = Pattern.compile(
            "^(?:\\[coreprotect]\\s*)?(?:error|no permission|invalid (?:lookup|parameters?))(?:[:.!].*)?$",
            Pattern.CASE_INSENSITIVE);

    private final ScanKind kind;

    public CoreProtectLookupParser(ScanKind kind) {
        this.kind = java.util.Objects.requireNonNull(kind);
    }

    public ParsedResponse<List<LookupEvent>> parse(String line) {
        String value = line == null ? "" : line.trim();
        if (NO_RESULTS.matcher(value).matches()) {
            return ParsedResponse.confirmed(List.of(new NoResults()));
        }
        if (REJECTED.matcher(value).matches()) {
            return ParsedResponse.rejected(List.of(new Rejected(value)));
        }

        List<LookupEvent> events = new ArrayList<>();
        Pattern entryPattern = kind == ScanKind.CONTAINER ? CONTAINER_ENTRY : BLOCK_ENTRY;
        Matcher entry = entryPattern.matcher(value);
        try {
            while (entry.find()) {
                long count = entry.group(3) == null ? 1L : Long.parseLong(entry.group(3));
                if (count < 1) return ParsedResponse.rejected(List.of(new Malformed(value)));
                events.add(new Entry(entry.group(1), entry.group(2).toLowerCase(Locale.ROOT), count,
                        entry.group(4).toLowerCase(Locale.ROOT)));
            }

            Matcher page = PAGE.matcher(value);
            if (page.find()) {
                int current = Integer.parseInt(page.group(1));
                int total = Integer.parseInt(page.group(2));
                if (current < 1 || total < current) {
                    return ParsedResponse.rejected(List.of(new Malformed(value)));
                }
                events.add(new Page(current, total));
            }
        } catch (NumberFormatException exception) {
            return ParsedResponse.rejected(List.of(new Malformed(value)));
        }

        return events.isEmpty() ? ParsedResponse.unrelated() : ParsedResponse.progress(List.copyOf(events));
    }

    public enum ScanKind { CONTAINER, BLOCK }

    public sealed interface LookupEvent permits Entry, Page, NoResults, Rejected, Malformed {
    }

    public record Entry(String player, String action, long count, String material) implements LookupEvent {
    }

    public record Page(int current, int total) implements LookupEvent {
    }

    public record NoResults() implements LookupEvent {
    }

    public record Rejected(String line) implements LookupEvent {
    }

    public record Malformed(String line) implements LookupEvent {
    }
}
