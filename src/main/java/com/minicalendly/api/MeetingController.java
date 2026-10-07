package com.minicalendly.api;

import com.minicalendly.api.dto.MeetingResponse;
import com.minicalendly.api.dto.PageResponse;
import com.minicalendly.api.dto.ScheduleMeetingRequest;
import com.minicalendly.api.dto.UpdateMeetingRequest;
import com.minicalendly.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/users/{userId}/meetings")
@Tag(name = "Meetings", description = "Meetings booked on a user's free slots")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert a FREE slot of the user into a meeting",
            description = "The slot becomes BUSY. Returns 409 if the slot is busy or already booked.")
    public MeetingResponse schedule(@PathVariable Long userId, @Valid @RequestBody ScheduleMeetingRequest request) {
        return meetingService.schedule(userId, request);
    }

    @GetMapping
    @Operation(summary = "List meetings the user organises or participates in, overlapping [from, to)")
    public PageResponse<MeetingResponse> list(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(meetingService.list(userId, from.toInstant(), to.toInstant(), pageable));
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Get a meeting the user organises or participates in")
    public MeetingResponse get(@PathVariable Long userId, @PathVariable Long meetingId) {
        return meetingService.get(userId, meetingId);
    }

    @PatchMapping("/{meetingId}")
    @Operation(summary = "Update title, description or participants (organiser only)")
    public MeetingResponse update(@PathVariable Long userId, @PathVariable Long meetingId,
                                  @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(userId, meetingId, request);
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a meeting (organiser only); its slot becomes FREE again")
    public void cancel(@PathVariable Long userId, @PathVariable Long meetingId) {
        meetingService.cancel(userId, meetingId);
    }
}
