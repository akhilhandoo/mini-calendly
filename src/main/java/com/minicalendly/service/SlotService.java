package com.minicalendly.service;

import com.minicalendly.api.dto.CreateSlotBatchRequest;
import com.minicalendly.api.dto.CreateSlotRequest;
import com.minicalendly.api.dto.SlotResponse;
import com.minicalendly.api.dto.UpdateSlotRequest;
import com.minicalendly.domain.Calendar;
import com.minicalendly.domain.SlotStatus;
import com.minicalendly.domain.TimeSlot;
import com.minicalendly.repository.MeetingRepository;
import com.minicalendly.repository.SlotWithMeeting;
import com.minicalendly.repository.TimeSlotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class SlotService {

    /** Sentinel for "no slot to exclude" in overlap checks; sequence ids start at 1. */
    private static final long NO_SLOT = -1L;

    private final TimeSlotRepository slots;
    private final MeetingRepository meetings;
    private final CalendarService calendarService;
    private final TimeRules timeRules;
    private final Counter slotsCreated;

    public SlotService(TimeSlotRepository slots, MeetingRepository meetings, CalendarService calendarService,
                       TimeRules timeRules, MeterRegistry meterRegistry) {
        this.slots = slots;
        this.meetings = meetings;
        this.calendarService = calendarService;
        this.timeRules = timeRules;
        this.slotsCreated = Counter.builder("calendar.slots.added")
                .description("Number of time slots created")
                .register(meterRegistry);
    }

    public SlotResponse create(Long userId, CreateSlotRequest request) {
        Instant start = request.startTime().toInstant();
        Instant end = resolveEnd(start, request.endTime(), request.durationMinutes());
        timeRules.validateSlot(start, end);

        Calendar calendar = calendarService.lockCalendarOf(userId);
        ensureNoOverlap(calendar, start, end, NO_SLOT);
        TimeSlot slot = slots.save(new TimeSlot(calendar, start, end, statusOrFree(request.status()), timeRules.now()));
        slotsCreated.increment();
        return Mappers.toResponse(slot, null);
    }

    /** Splits [from, to) into consecutive slots. All-or-nothing: any overlap rejects the whole batch. */
    public List<SlotResponse> createBatch(Long userId, CreateSlotBatchRequest request) {
        Instant from = request.from().toInstant();
        Instant to = request.to().toInstant();
        Duration slotDuration = Duration.ofMinutes(request.slotDurationMinutes());
        if (!to.isAfter(from)) {
            throw new InvalidRequestException("'to' must be after 'from'");
        }
        timeRules.validateSlotDuration(slotDuration);
        long count = Duration.between(from, to).dividedBy(slotDuration);
        if (count == 0) {
            throw new InvalidRequestException("The range is shorter than one slot");
        }
        if (count > timeRules.maxBatchSize()) {
            throw new InvalidRequestException("A batch may create at most %d slots, requested %d"
                    .formatted(timeRules.maxBatchSize(), count));
        }
        Instant end = from.plus(slotDuration.multipliedBy(count));
        timeRules.validateSlot(from, from.plus(slotDuration));

        Calendar calendar = calendarService.lockCalendarOf(userId);
        ensureNoOverlap(calendar, from, end, NO_SLOT);
        SlotStatus status = statusOrFree(request.status());
        Instant now = timeRules.now();
        List<TimeSlot> batch = new ArrayList<>((int) count);
        for (Instant start = from; start.isBefore(end); start = start.plus(slotDuration)) {
            batch.add(new TimeSlot(calendar, start, start.plus(slotDuration), status, now));
        }
        List<TimeSlot> saved = slots.saveAll(batch);
        slotsCreated.increment(saved.size());
        return saved.stream().map(slot -> Mappers.toResponse(slot, null)).toList();
    }

    @Transactional(readOnly = true)
    public SlotResponse get(Long userId, Long slotId) {
        Calendar calendar = calendarService.calendarOf(userId);
        return Mappers.toResponse(findSlot(calendar, slotId));
    }

    @Transactional(readOnly = true)
    public Page<SlotResponse> search(Long userId, Instant from, Instant to, SlotStatus status, Pageable pageable) {
        timeRules.validateQueryRange(from, to);
        Calendar calendar = calendarService.calendarOf(userId);
        return slots.search(calendar.getId(), from, to, status, pageable).map(Mappers::toResponse);
    }

    public SlotResponse update(Long userId, Long slotId, UpdateSlotRequest request) {
        Calendar calendar = calendarService.lockCalendarOf(userId);
        SlotWithMeeting row = findSlot(calendar, slotId);
        TimeSlot slot = row.slot();
        boolean booked = row.meetingId() != null;

        boolean moving = request.startTime() != null || request.endTime() != null || request.durationMinutes() != null;
        if (moving) {
            if (booked) {
                throw bookedConflict(slotId, row.meetingId(), "moved");
            }
            Instant start = request.startTime() != null ? request.startTime().toInstant() : slot.getStartTime();
            Instant end = request.endTime() == null && request.durationMinutes() == null
                    ? start.plus(slot.duration())
                    : resolveEnd(start, request.endTime(), request.durationMinutes());
            timeRules.validateSlot(start, end);
            ensureNoOverlap(calendar, start, end, slot.getId());
            slot.reschedule(start, end, timeRules.now());
        }
        if (request.status() != null && request.status() != slot.getStatus()) {
            if (booked) {
                throw bookedConflict(slotId, row.meetingId(), "freed");
            }
            slot.changeStatus(request.status(), timeRules.now());
        }
        slots.flush();
        return Mappers.toResponse(slot, row.meetingId());
    }

    public void delete(Long userId, Long slotId) {
        Calendar calendar = calendarService.lockCalendarOf(userId);
        SlotWithMeeting row = findSlot(calendar, slotId);
        if (row.meetingId() != null) {
            throw bookedConflict(slotId, row.meetingId(), "deleted");
        }
        slots.delete(row.slot());
    }

    private SlotWithMeeting findSlot(Calendar calendar, Long slotId) {
        return slots.findWithMeeting(slotId, calendar.getId())
                .orElseThrow(() -> new NotFoundException("Slot %d not found".formatted(slotId)));
    }

    private void ensureNoOverlap(Calendar calendar, Instant start, Instant end, long excludeId) {
        if (slots.existsOverlapping(calendar.getId(), start, end, excludeId)) {
            throw new ConflictException("The time range %s - %s overlaps an existing slot".formatted(start, end));
        }
    }

    private static Instant resolveEnd(Instant start, OffsetDateTime endTime, Integer durationMinutes) {
        if (endTime != null && durationMinutes != null) {
            throw new InvalidRequestException("Provide either endTime or durationMinutes, not both");
        }
        if (endTime == null && durationMinutes == null) {
            throw new InvalidRequestException("Either endTime or durationMinutes is required");
        }
        return endTime != null ? endTime.toInstant() : start.plus(Duration.ofMinutes(durationMinutes));
    }

    private static SlotStatus statusOrFree(SlotStatus status) {
        return Objects.requireNonNullElse(status, SlotStatus.FREE);
    }

    private static ConflictException bookedConflict(Long slotId, Long meetingId, String action) {
        return new ConflictException("Slot %d is booked by meeting %d and cannot be %s; cancel the meeting first"
                .formatted(slotId, meetingId, action));
    }
}
