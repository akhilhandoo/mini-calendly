package com.minicalendly.api.dto;

import com.minicalendly.domain.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

@Schema(description = "Partial update; omitted fields keep their value. "
        + "Use endTime or durationMinutes, not both. A booked slot can't be moved or freed.")
public record UpdateSlotRequest(
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        @Positive Integer durationMinutes,
        SlotStatus status) {
}
