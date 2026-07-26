package com.duperknight.client.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure interval operations for one-hour war-backed purge windows. */
public final class WarTimeline {
    private WarTimeline() {
    }

    public static List<Interval> merge(List<Interval> intervals) {
        List<Interval> sorted = intervals.stream()
                .filter(interval -> interval != null && interval.endMillis() > interval.startMillis())
                .sorted(Comparator.comparingLong(Interval::startMillis)
                        .thenComparingLong(Interval::endMillis))
                .toList();
        if (sorted.isEmpty()) return List.of();

        List<Interval> merged = new ArrayList<>();
        long start = sorted.getFirst().startMillis();
        long end = sorted.getFirst().endMillis();
        for (int index = 1; index < sorted.size(); index++) {
            Interval next = sorted.get(index);
            if (next.startMillis() <= end) {
                end = Math.max(end, next.endMillis());
            } else {
                merged.add(new Interval(start, end));
                start = next.startMillis();
                end = next.endMillis();
            }
        }
        merged.add(new Interval(start, end));
        return List.copyOf(merged);
    }

    public static boolean activeAt(List<Interval> intervals, long now) {
        return merge(intervals).stream()
                .anyMatch(interval -> now >= interval.startMillis() && now < interval.endMillis());
    }

    public record Interval(long startMillis, long endMillis) {
        public Interval {
            if (endMillis <= startMillis) throw new IllegalArgumentException("endMillis");
        }
    }
}
