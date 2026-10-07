package com.minicalendly.api.dto;

import java.time.Instant;

public record UserResponse(Long id, String name, String email, String timeZone, Instant createdAt) {
}
