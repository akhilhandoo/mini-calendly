package com.minicalendly.api.dto;

import java.time.Instant;

public record IntervalResponse(Instant start, Instant end, long durationMinutes) {
}
