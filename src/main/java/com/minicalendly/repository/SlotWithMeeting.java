package com.minicalendly.repository;

import com.minicalendly.domain.TimeSlot;

/** A slot together with the id of the meeting booked on it, if any. */
public record SlotWithMeeting(TimeSlot slot, Long meetingId) {
}
