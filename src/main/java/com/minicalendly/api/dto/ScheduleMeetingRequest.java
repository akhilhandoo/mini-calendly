package com.minicalendly.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScheduleMeetingRequest(
        @NotNull Long slotId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 100) List<@Valid @NotNull ParticipantRequest> participants) {
}
