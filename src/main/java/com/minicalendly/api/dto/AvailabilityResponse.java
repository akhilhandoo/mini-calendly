package com.minicalendly.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated free/busy view: adjacent or overlapping slots of the same status are merged
 * and clipped to the requested window. Time not covered by any slot is neither free nor busy.
 */
public record AvailabilityResponse(
        Long userId,
        Instant from,
        Instant to,
        List<IntervalResponse> free,
        List<IntervalResponse> busy,
        long freeMinutes,
        long busyMinutes) {
}
