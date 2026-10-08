package com.minicalendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunable business limits, bound from the {@code calendar.*} configuration namespace.
 */
@ConfigurationProperties("calendar")
public record CalendarProperties(@DefaultValue Slots slots, @DefaultValue Query query, @DefaultValue Seed seed) {

    public record Slots(
            @DefaultValue("5m") Duration minDuration,
            @DefaultValue("8h") Duration maxDuration,
            @DefaultValue("500") int maxBatchSize,
            @DefaultValue("false") boolean allowPast) {
    }

    public record Query(
            @DefaultValue("93d") Duration maxRange,
            @DefaultValue("20") int maxUsersForCommonAvailability) {
    }

    /** Demo data inserted on startup into an empty database (see {@code SeedDataLoader}). */
    public record Seed(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("classpath:seed/seed-data.json") String location) {
    }
}
