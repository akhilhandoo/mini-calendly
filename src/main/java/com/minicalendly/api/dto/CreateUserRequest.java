package com.minicalendly.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "IANA time zone of the user's calendar", example = "Europe/Berlin", defaultValue = "UTC")
        @Size(max = 64) String timeZone) {
}
