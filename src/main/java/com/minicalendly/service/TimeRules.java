package com.minicalendly.service;

import com.minicalendly.config.CalendarProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Central place for the configurable time-related business rules. */
@Component
public class TimeRules {

    private final CalendarProperties properties;
    private final Clock clock;

    public TimeRules(CalendarProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public void validateSlot(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("Slot end must be after its start");
        }
        validateSlotDuration(Duration.between(start, end));
        if (!properties.slots().allowPast() && start.isBefore(now())) {
            throw new InvalidRequestException("Slots cannot start in the past");
        }
    }

    public void validateSlotDuration(Duration duration) {
        Duration min = properties.slots().minDuration();
        Duration max = properties.slots().maxDuration();
        if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
            throw new InvalidRequestException("Slot duration must be between %d and %d minutes"
                    .formatted(min.toMinutes(), max.toMinutes()));
        }
    }

    public int maxBatchSize() {
        return properties.slots().maxBatchSize();
    }

    public void validateQueryRange(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new InvalidRequestException("'to' must be after 'from'");
        }
        Duration maxRange = properties.query().maxRange();
        if (Duration.between(from, to).compareTo(maxRange) > 0) {
            throw new InvalidRequestException("Query range must not exceed %d days".formatted(maxRange.toDays()));
        }
    }

    public int maxUsersForCommonAvailability() {
        return properties.query().maxUsersForCommonAvailability();
    }
}
