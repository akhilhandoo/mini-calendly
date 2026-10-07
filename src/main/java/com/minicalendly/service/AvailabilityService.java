package com.minicalendly.service;

import com.minicalendly.api.dto.AvailabilityResponse;
import com.minicalendly.api.dto.CommonAvailabilityResponse;
import com.minicalendly.domain.Calendar;
import com.minicalendly.repository.CalendarRepository;
import com.minicalendly.repository.SlotInterval;
import com.minicalendly.repository.TimeSlotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final TimeSlotRepository slots;
    private final CalendarRepository calendars;
    private final CalendarService calendarService;
    private final TimeRules timeRules;
    private final Timer userTimer;
    private final Timer commonTimer;

    public AvailabilityService(TimeSlotRepository slots, CalendarRepository calendars,
                               CalendarService calendarService, TimeRules timeRules, MeterRegistry meterRegistry) {
        this.slots = slots;
        this.calendars = calendars;
        this.calendarService = calendarService;
        this.timeRules = timeRules;
        this.userTimer = Timer.builder("calendar.availability.query").tag("type", "user")
                .description("Free/busy aggregation latency").register(meterRegistry);
        this.commonTimer = Timer.builder("calendar.availability.query").tag("type", "common")
                .description("Free/busy aggregation latency").register(meterRegistry);
    }

    /** Aggregated free/busy view of one user's calendar over [from, to). */
    public AvailabilityResponse forUser(Long userId, Instant from, Instant to) {
        return userTimer.record(() -> {
            timeRules.validateQueryRange(from, to);
            Calendar calendar = calendarService.calendarOf(userId);
            var freeBusy = AvailabilityAggregator.aggregate(
                    slots.findIntervals(List.of(calendar.getId()), from, to), from, to);
            return new AvailabilityResponse(userId, from, to,
                    Mappers.toResponse(freeBusy.free()), Mappers.toResponse(freeBusy.busy()),
                    AvailabilityAggregator.total(freeBusy.free()).toMinutes(),
                    AvailabilityAggregator.total(freeBusy.busy()).toMinutes());
        });
    }

    /** Windows in which all given users have free slots - useful for finding a common meeting time. */
    public CommonAvailabilityResponse common(List<Long> requestedUserIds, Instant from, Instant to) {
        return commonTimer.record(() -> {
            timeRules.validateQueryRange(from, to);
            Set<Long> userIds = new LinkedHashSet<>(requestedUserIds);
            if (userIds.isEmpty()) {
                throw new InvalidRequestException("At least one user id is required");
            }
            if (userIds.size() > timeRules.maxUsersForCommonAvailability()) {
                throw new InvalidRequestException("At most %d users can be compared at once"
                        .formatted(timeRules.maxUsersForCommonAvailability()));
            }
            Map<Long, Long> calendarIdByUser = calendars.findByOwnerIds(userIds).stream()
                    .collect(Collectors.toMap(c -> c.getOwner().getId(), Calendar::getId));
            userIds.stream().filter(id -> !calendarIdByUser.containsKey(id)).findFirst().ifPresent(missing -> {
                throw CalendarService.userNotFound(missing);
            });

            // One query for all calendars, already ordered by calendar then start time.
            Map<Long, List<SlotInterval>> byCalendar = slots.findIntervals(calendarIdByUser.values(), from, to)
                    .stream()
                    .collect(Collectors.groupingBy(SlotInterval::calendarId, Collectors.toList()));
            List<List<AvailabilityAggregator.Interval>> freePerUser = new ArrayList<>();
            for (Long userId : userIds) {
                List<SlotInterval> userSlots = byCalendar.getOrDefault(calendarIdByUser.get(userId), List.of());
                freePerUser.add(AvailabilityAggregator.aggregate(userSlots, from, to).free());
            }
            var common = AvailabilityAggregator.intersectAll(freePerUser);
            return new CommonAvailabilityResponse(List.copyOf(userIds), from, to,
                    Mappers.toResponse(common), AvailabilityAggregator.total(common).toMinutes());
        });
    }
}
