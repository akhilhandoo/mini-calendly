package com.minicalendly.service;

import com.minicalendly.api.dto.CreateUserRequest;
import com.minicalendly.api.dto.UserResponse;
import com.minicalendly.domain.Calendar;
import com.minicalendly.domain.User;
import com.minicalendly.repository.CalendarRepository;
import com.minicalendly.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final CalendarRepository calendars;
    private final CalendarService calendarService;
    private final TimeRules timeRules;

    public UserService(UserRepository users, CalendarRepository calendars,
                       CalendarService calendarService, TimeRules timeRules) {
        this.users = users;
        this.calendars = calendars;
        this.calendarService = calendarService;
        this.timeRules = timeRules;
    }

    /** Registers a user together with their personal calendar. */
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("A user with e-mail %s already exists".formatted(email));
        }
        ZoneId zone = parseZone(request.timeZone());
        Instant now = timeRules.now();
        User user = users.save(new User(request.name().strip(), email, now));
        Calendar calendar = calendars.save(new Calendar(user, zone, now));
        return Mappers.toResponse(calendar);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return Mappers.toResponse(calendarService.calendarOf(userId));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return calendars.findAll(pageable).map(Mappers::toResponse);
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone.strip());
        } catch (DateTimeException e) {
            throw new InvalidRequestException("Unknown time zone: " + timeZone);
        }
    }
}
