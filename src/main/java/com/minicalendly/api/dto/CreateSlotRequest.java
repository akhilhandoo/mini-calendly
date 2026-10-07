package com.minicalendly.api.dto;

import com.minicalendly.domain.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

@Schema(description = "Provide either endTime or durationMinutes.")
public record CreateSlotRequest(
        @NotNull @Schema(example = "2030-01-15T09:00:00Z") OffsetDateTime startTime,
        @Schema(example = "2030-01-15T09:30:00Z") OffsetDateTime endTime,
        @Positive @Schema(example = "30") Integer durationMinutes,
        @Schema(defaultValue = "FREE") SlotStatus status) {
}
