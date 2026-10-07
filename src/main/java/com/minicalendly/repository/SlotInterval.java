package com.minicalendly.repository;

import com.minicalendly.domain.SlotStatus;

import java.time.Instant;

/** Lightweight projection used by free/busy aggregation; avoids loading full entities. */
public record SlotInterval(Long calendarId, Instant start, Instant end, SlotStatus status) {
}
