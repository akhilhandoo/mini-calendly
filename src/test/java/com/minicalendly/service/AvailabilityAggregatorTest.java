package com.minicalendly.service;

import com.minicalendly.domain.SlotStatus;
import com.minicalendly.repository.SlotInterval;
import com.minicalendly.service.AvailabilityAggregator.Interval;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.minicalendly.domain.SlotStatus.BUSY;
import static com.minicalendly.domain.SlotStatus.FREE;
import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityAggregatorTest {

    private static final Instant T0 = Instant.parse("2030-01-15T00:00:00Z");

    private static Instant h(double hours) {
        return T0.plus(Duration.ofMinutes((long) (hours * 60)));
    }

    private static SlotInterval slot(double from, double to, SlotStatus status) {
        return new SlotInterval(1L, h(from), h(to), status);
    }

    private static Interval interval(double from, double to) {
        return new Interval(h(from), h(to));
    }

    @Test
    void mergesAdjacentSlotsOfSameStatus() {
        var result = AvailabilityAggregator.aggregate(List.of(
                slot(9, 9.5, FREE), slot(9.5, 10, FREE), slot(10, 10.5, BUSY), slot(10.5, 11, FREE)), h(0), h(24));

        assertThat(result.free()).containsExactly(interval(9, 10), interval(10.5, 11));
        assertThat(result.busy()).containsExactly(interval(10, 10.5));
    }

    @Test
    void keepsGapsBetweenSlots() {
        var result = AvailabilityAggregator.aggregate(List.of(slot(9, 10, FREE), slot(11, 12, FREE)), h(0), h(24));

        assertThat(result.free()).containsExactly(interval(9, 10), interval(11, 12));
        assertThat(AvailabilityAggregator.total(result.free())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void clipsSlotsToRequestedWindow() {
        var result = AvailabilityAggregator.aggregate(List.of(slot(8, 10, FREE), slot(10, 13, BUSY)), h(9), h(12));

        assertThat(result.free()).containsExactly(interval(9, 10));
        assertThat(result.busy()).containsExactly(interval(10, 12));
    }

    @Test
    void emptyInputGivesEmptyResult() {
        var result = AvailabilityAggregator.aggregate(List.of(), h(0), h(24));

        assertThat(result.free()).isEmpty();
        assertThat(result.busy()).isEmpty();
    }

    @Test
    void intersectsFreeTimeOfSeveralUsers() {
        var alice = List.of(interval(9, 12), interval(14, 17));
        var bob = List.of(interval(10, 15));
        var carol = List.of(interval(8, 11), interval(14.5, 18));

        assertThat(AvailabilityAggregator.intersectAll(List.of(alice, bob, carol)))
                .containsExactly(interval(10, 11), interval(14.5, 15));
    }

    @Test
    void touchingIntervalsDoNotIntersect() {
        assertThat(AvailabilityAggregator.intersectAll(List.of(
                List.of(interval(9, 10)), List.of(interval(10, 11))))).isEmpty();
    }

    @Test
    void intersectionWithEmptyListIsEmpty() {
        assertThat(AvailabilityAggregator.intersectAll(List.of(List.of(interval(9, 10)), List.of()))).isEmpty();
    }
}
