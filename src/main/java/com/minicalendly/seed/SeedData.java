package com.minicalendly.seed;

import com.minicalendly.domain.SlotStatus;

import java.time.LocalTime;
import java.util.List;

/**
 * Shape of {@code seed/seed-data.json}. Times are relative so the demo data never goes stale:
 * {@code day} counts days from the Monday of the current week and {@code start} is a wall-clock time,
 * both in the owning user's time zone.
 */
record SeedData(List<SeedUser> users) {

    record SeedUser(String name, String email, String timeZone, int createdDaysAgo, List<SeedSlot> slots) {
    }

    record SeedSlot(int day, LocalTime start, int minutes, SlotStatus status, SeedMeeting meeting) {
    }

    record SeedMeeting(String title, String description, List<SeedParticipant> participants, Boolean updated) {
    }

    record SeedParticipant(String email, String name) {
    }
}
