package com.minicalendly.service;

import com.minicalendly.api.dto.MeetingResponse;
import com.minicalendly.api.dto.ParticipantRequest;
import com.minicalendly.api.dto.ScheduleMeetingRequest;
import com.minicalendly.api.dto.UpdateMeetingRequest;
import com.minicalendly.domain.Calendar;
import com.minicalendly.domain.Meeting;
import com.minicalendly.domain.Participant;
import com.minicalendly.domain.SlotStatus;
import com.minicalendly.domain.TimeSlot;
import com.minicalendly.domain.User;
import com.minicalendly.repository.MeetingRepository;
import com.minicalendly.repository.TimeSlotRepository;
import com.minicalendly.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class MeetingService {

    private final MeetingRepository meetings;
    private final TimeSlotRepository slots;
    private final UserRepository users;
    private final CalendarService calendarService;
    private final TimeRules timeRules;
    private final Counter meetingsScheduled;
    private final Counter meetingsCancelled;

    public MeetingService(MeetingRepository meetings, TimeSlotRepository slots, UserRepository users,
                          CalendarService calendarService, TimeRules timeRules, MeterRegistry meterRegistry) {
        this.meetings = meetings;
        this.slots = slots;
        this.users = users;
        this.calendarService = calendarService;
        this.timeRules = timeRules;
        this.meetingsScheduled = Counter.builder("calendar.meetings.scheduled")
                .description("Number of free slots converted into meetings")
                .register(meterRegistry);
        this.meetingsCancelled = Counter.builder("calendar.meetings.cancelled")
                .description("Number of meetings cancelled")
                .register(meterRegistry);
    }

    /** Converts a free slot of the organiser into a meeting; the slot becomes busy. */
    public MeetingResponse schedule(Long organizerId, ScheduleMeetingRequest request) {
        Calendar calendar = calendarService.lockCalendarOf(organizerId);
        TimeSlot slot = slots.findByIdAndCalendarId(request.slotId(), calendar.getId())
                .orElseThrow(() -> new NotFoundException("Slot %d not found".formatted(request.slotId())));
        if (meetings.existsBySlotId(slot.getId())) {
            throw new ConflictException("Slot %d is already booked".formatted(slot.getId()));
        }
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("Slot %d is marked busy".formatted(slot.getId()));
        }
        Instant now = timeRules.now();
        if (!slot.getEndTime().isAfter(now)) {
            throw new ConflictException("Slot %d is already over".formatted(slot.getId()));
        }
        slot.changeStatus(SlotStatus.BUSY, now);
        Meeting meeting = meetings.save(new Meeting(slot, request.title().strip(), request.description(),
                resolveParticipants(request.participants()), now));
        meetingsScheduled.increment();
        return Mappers.toResponse(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(Long userId, Long meetingId) {
        Calendar calendar = calendarService.calendarOf(userId);
        return meetings.findVisibleTo(meetingId, calendar.getId(), userId)
                .map(Mappers::toResponse)
                .orElseThrow(() -> meetingNotFound(meetingId));
    }

    /** Meetings the user organises or participates in, overlapping [from, to). */
    @Transactional(readOnly = true)
    public Page<MeetingResponse> list(Long userId, Instant from, Instant to, Pageable pageable) {
        timeRules.validateQueryRange(from, to);
        Calendar calendar = calendarService.calendarOf(userId);
        return meetings.findVisibleTo(calendar.getId(), userId, from, to, pageable).map(Mappers::toResponse);
    }

    public MeetingResponse update(Long organizerId, Long meetingId, UpdateMeetingRequest request) {
        Calendar calendar = calendarService.lockCalendarOf(organizerId);
        Meeting meeting = findOrganised(calendar, meetingId);
        Instant now = timeRules.now();
        meeting.updateDetails(request.title() == null ? null : request.title().strip(), request.description(), now);
        if (request.participants() != null) {
            meeting.replaceParticipants(resolveParticipants(request.participants()), now);
        }
        meetings.flush();
        return Mappers.toResponse(meeting);
    }

    /** Cancels the meeting and frees the underlying slot again. */
    public void cancel(Long organizerId, Long meetingId) {
        Calendar calendar = calendarService.lockCalendarOf(organizerId);
        Meeting meeting = findOrganised(calendar, meetingId);
        meeting.getSlot().changeStatus(SlotStatus.FREE, timeRules.now());
        meetings.delete(meeting);
        meetingsCancelled.increment();
    }

    private Meeting findOrganised(Calendar calendar, Long meetingId) {
        return meetings.findOrganisedBy(meetingId, calendar.getId()).orElseThrow(() -> meetingNotFound(meetingId));
    }

    /** De-duplicates by e-mail and links participants that are registered users. */
    private List<Participant> resolveParticipants(List<ParticipantRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<String, ParticipantRequest> byEmail = new LinkedHashMap<>();
        requested.forEach(p -> byEmail.putIfAbsent(UserService.normalizeEmail(p.email()), p));
        Map<String, Long> userIds = users.findByEmailIn(byEmail.keySet()).stream()
                .collect(Collectors.toMap(User::getEmail, User::getId));
        return byEmail.entrySet().stream()
                .map(e -> new Participant(e.getKey(), e.getValue().name(), userIds.get(e.getKey())))
                .toList();
    }

    private static NotFoundException meetingNotFound(Long meetingId) {
        return new NotFoundException("Meeting %d not found".formatted(meetingId));
    }
}
