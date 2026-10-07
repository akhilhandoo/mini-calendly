package com.minicalendly.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Partial update; omitted fields keep their value. A participants list replaces the existing one.")
public record UpdateMeetingRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 4000) String description,
        @Size(max = 100) List<@Valid @NotNull ParticipantRequest> participants) {
}
