package com.minicalendly.service;

import com.minicalendly.domain.Calendar;
import com.minicalendly.repository.CalendarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a user's calendar; the API never exposes calendars directly. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CalendarService {

    private final CalendarRepository calendars;

    public CalendarService(CalendarRepository calendars) {
        this.calendars = calendars;
    }

    public Calendar calendarOf(Long userId) {
        return calendars.findByOwnerId(userId).orElseThrow(() -> userNotFound(userId));
    }

    /** Loads the calendar with a row lock, serialising concurrent writes for the same user. */
    public Calendar lockCalendarOf(Long userId) {
        return calendars.lockByOwnerId(userId).orElseThrow(() -> userNotFound(userId));
    }

    static NotFoundException userNotFound(Long userId) {
        return new NotFoundException("User %d not found".formatted(userId));
    }
}
