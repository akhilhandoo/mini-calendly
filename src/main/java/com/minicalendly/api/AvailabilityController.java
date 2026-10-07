package com.minicalendly.api;

import com.minicalendly.api.dto.AvailabilityResponse;
import com.minicalendly.api.dto.CommonAvailabilityResponse;
import com.minicalendly.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Availability", description = "Aggregated free/busy views")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/users/{userId}/availability")
    @Operation(summary = "Merged free and busy intervals of a user over [from, to)")
    public AvailabilityResponse forUser(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return availabilityService.forUser(userId, from.toInstant(), to.toInstant());
    }

    @GetMapping("/availability/common")
    @Operation(summary = "Intervals in which all given users are free")
    public CommonAvailabilityResponse common(
            @RequestParam List<Long> userIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return availabilityService.common(userIds, from.toInstant(), to.toInstant());
    }
}
