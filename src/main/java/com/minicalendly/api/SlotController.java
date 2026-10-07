package com.minicalendly.api;

import com.minicalendly.api.dto.CreateSlotBatchRequest;
import com.minicalendly.api.dto.CreateSlotRequest;
import com.minicalendly.api.dto.PageResponse;
import com.minicalendly.api.dto.SlotResponse;
import com.minicalendly.api.dto.UpdateSlotRequest;
import com.minicalendly.domain.SlotStatus;
import com.minicalendly.service.SlotService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/slots")
@Tag(name = "Time slots", description = "Availability slots in a user's calendar")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a slot", description = "Slots of one user may not overlap.")
    public SlotResponse create(@PathVariable Long userId, @Valid @RequestBody CreateSlotRequest request) {
        return slotService.create(userId, request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create consecutive slots of a fixed duration over a time range")
    public List<SlotResponse> createBatch(@PathVariable Long userId, @Valid @RequestBody CreateSlotBatchRequest request) {
        return slotService.createBatch(userId, request);
    }

    @GetMapping
    @Operation(summary = "List slots overlapping [from, to), ordered by start time")
    public PageResponse<SlotResponse> search(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) SlotStatus status,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(slotService.search(userId, from.toInstant(), to.toInstant(), status, pageable));
    }

    @GetMapping("/{slotId}")
    @Operation(summary = "Get a slot")
    public SlotResponse get(@PathVariable Long userId, @PathVariable Long slotId) {
        return slotService.get(userId, slotId);
    }

    @PatchMapping("/{slotId}")
    @Operation(summary = "Move/resize a slot or mark it FREE/BUSY")
    public SlotResponse update(@PathVariable Long userId, @PathVariable Long slotId,
                               @Valid @RequestBody UpdateSlotRequest request) {
        return slotService.update(userId, slotId, request);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a slot", description = "Booked slots can't be deleted; cancel the meeting first.")
    public void delete(@PathVariable Long userId, @PathVariable Long slotId) {
        slotService.delete(userId, slotId);
    }
}
