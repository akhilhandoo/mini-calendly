package com.minicalendly.api.dto;

import java.time.Instant;
import java.util.List;

/** Time windows in which every requested user has a free slot. */
public record CommonAvailabilityResponse(
        List<Long> userIds,
        Instant from,
        Instant to,
        List<IntervalResponse> free,
        long freeMinutes) {
}
