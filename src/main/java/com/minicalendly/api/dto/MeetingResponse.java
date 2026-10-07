package com.minicalendly.api.dto;

import java.time.Instant;
import java.util.List;

public record MeetingResponse(
        Long id,
        Long organizerId,
        Long slotId,
        Instant startTime,
        Instant endTime,
        String title,
        String description,
        List<ParticipantResponse> participants,
        Instant createdAt,
        Instant updatedAt) {
}
