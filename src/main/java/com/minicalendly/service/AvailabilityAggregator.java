package com.minicalendly.service;

import com.minicalendly.domain.SlotStatus;
import com.minicalendly.repository.SlotInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, allocation-light interval arithmetic behind the free/busy views. All methods run in
 * linear time over their (already sorted) input, so a window with thousands of slots is cheap.
 */
public final class AvailabilityAggregator {

    public record Interval(Instant start, Instant end) {

        public Duration duration() {
            return Duration.between(start, end);
        }
    }

    public record FreeBusy(List<Interval> free, List<Interval> busy) {
    }

    private AvailabilityAggregator() {
    }

    /**
     * Clips slots to [from, to) and merges touching or overlapping slots of the same status.
     *
     * @param slots slots of a single calendar, sorted by start time
     */
    public static FreeBusy aggregate(List<SlotInterval> slots, Instant from, Instant to) {
        List<Interval> free = new ArrayList<>();
        List<Interval> busy = new ArrayList<>();
        for (SlotInterval slot : slots) {
            Instant start = max(slot.start(), from);
            Instant end = min(slot.end(), to);
            if (start.isBefore(end)) {
                appendMerged(slot.status() == SlotStatus.FREE ? free : busy, new Interval(start, end));
            }
        }
        return new FreeBusy(free, busy);
    }

    /** Intersection of several sorted, non-overlapping interval lists. */
    public static List<Interval> intersectAll(List<List<Interval>> lists) {
        if (lists.isEmpty()) {
            return List.of();
        }
        List<Interval> result = lists.getFirst();
        for (int i = 1; i < lists.size() && !result.isEmpty(); i++) {
            result = intersect(result, lists.get(i));
        }
        return result;
    }

    static List<Interval> intersect(List<Interval> a, List<Interval> b) {
        List<Interval> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.size() && j < b.size()) {
            Interval x = a.get(i);
            Interval y = b.get(j);
            Instant start = max(x.start(), y.start());
            Instant end = min(x.end(), y.end());
            if (start.isBefore(end)) {
                result.add(new Interval(start, end));
            }
            if (x.end().isBefore(y.end())) {
                i++;
            } else {
                j++;
            }
        }
        return result;
    }

    public static Duration total(List<Interval> intervals) {
        return intervals.stream().map(Interval::duration).reduce(Duration.ZERO, Duration::plus);
    }

    private static void appendMerged(List<Interval> target, Interval next) {
        if (!target.isEmpty()) {
            Interval last = target.getLast();
            if (!next.start().isAfter(last.end())) {
                target.set(target.size() - 1, new Interval(last.start(), max(last.end(), next.end())));
                return;
            }
        }
        target.add(next);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
