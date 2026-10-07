package com.minicalendly.service;

import com.minicalendly.api.dto.IntervalResponse;
import com.minicalendly.api.dto.MeetingResponse;
import com.minicalendly.api.dto.ParticipantResponse;
import com.minicalendly.api.dto.SlotResponse;
import com.minicalendly.api.dto.UserResponse;
import com.minicalendly.domain.Calendar;
import com.minicalendly.domain.Meeting;
import com.minicalendly.domain.TimeSlot;
import com.minicalendly.repository.SlotWithMeeting;

import java.util.List;

final class Mappers {

    private Mappers() {
    }

    static UserResponse toResponse(Calendar calendar) {
        var user = calendar.getOwner();
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                calendar.getTimeZone().getId(), user.getCreatedAt());
    }

    static SlotResponse toResponse(SlotWithMeeting row) {
        return toResponse(row.slot(), row.meetingId());
    }

    static SlotResponse toResponse(TimeSlot slot, Long meetingId) {
        return new SlotResponse(slot.getId(), slot.getCalendar().getOwner().getId(),
                slot.getStartTime(), slot.getEndTime(), slot.duration().toMinutes(), slot.getStatus(), meetingId);
    }

    static MeetingResponse toResponse(Meeting meeting) {
        TimeSlot slot = meeting.getSlot();
        List<ParticipantResponse> participants = meeting.getParticipants().stream()
                .map(p -> new ParticipantResponse(p.getEmail(), p.getName(), p.getUserId()))
                .toList();
        return new MeetingResponse(meeting.getId(), slot.getCalendar().getOwner().getId(), slot.getId(),
                slot.getStartTime(), slot.getEndTime(), meeting.getTitle(), meeting.getDescription(),
                participants, meeting.getCreatedAt(), meeting.getUpdatedAt());
    }

    static List<IntervalResponse> toResponse(List<AvailabilityAggregator.Interval> intervals) {
        return intervals.stream()
                .map(i -> new IntervalResponse(i.start(), i.end(), i.duration().toMinutes()))
                .toList();
    }
}
