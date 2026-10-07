package com.minicalendly.api.dto;

import com.minicalendly.domain.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

@Schema(description = "Splits [from, to) into consecutive slots of slotDurationMinutes. "
        + "A trailing remainder shorter than the slot duration is ignored. All-or-nothing.")
public record CreateSlotBatchRequest(
        @NotNull @Schema(example = "2030-01-15T09:00:00Z") OffsetDateTime from,
        @NotNull @Schema(example = "2030-01-15T17:00:00Z") OffsetDateTime to,
        @NotNull @Positive @Schema(example = "30") Integer slotDurationMinutes,
        @Schema(defaultValue = "FREE") SlotStatus status) {
}
