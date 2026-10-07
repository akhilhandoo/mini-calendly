package com.minicalendly.api.dto;

import com.minicalendly.domain.SlotStatus;

import java.time.Instant;

public record SlotResponse(
        Long id,
        Long userId,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        SlotStatus status,
        Long meetingId) {
}
