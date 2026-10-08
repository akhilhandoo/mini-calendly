package com.minicalendly.seed;

import com.minicalendly.config.CalendarProperties;
import com.minicalendly.domain.Calendar;
import com.minicalendly.domain.Meeting;
import com.minicalendly.domain.Participant;
import com.minicalendly.domain.SlotStatus;
import com.minicalendly.domain.TimeSlot;
import com.minicalendly.domain.User;
import com.minicalendly.service.TimeRules;
import com.minicalendly.service.UserService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Populates an empty database with demo data from {@code calendar.seed.location} on startup.
 * It does nothing if any user exists, so restarts never duplicate data and real data is never touched.
 * All rows are written in one transaction: a broken seed file leaves the database empty.
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final CalendarProperties.Seed properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TimeRules timeRules;

    public SeedDataLoader(CalendarProperties properties, ResourceLoader resourceLoader, ObjectMapper objectMapper,
                          EntityManager entityManager, TransactionTemplate transaction, TimeRules timeRules) {
        this.properties = properties.seed();
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.transaction = transaction;
        this.timeRules = timeRules;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (properties.enabled()) {
            seed();
        }
    }

    /** Loads the seed file unless the database already has users; returns whether anything was inserted. */
    public boolean seed() throws IOException {
        SeedData data;
        try (InputStream in = resourceLoader.getResource(properties.location()).getInputStream()) {
            data = objectMapper.readValue(in, SeedData.class);
        }
        Boolean seeded = transaction.execute(status -> {
            Long existingUsers = entityManager.createQuery("select count(u) from User u", Long.class).getSingleResult();
            if (existingUsers > 0) {
                log.info("Skipping seed data: the database already contains {} users", existingUsers);
                return false;
            }
            long started = System.nanoTime();
            Counts counts = insert(data);
            log.info("Seeded {} users, {} slots and {} meetings from {} in {} ms", counts.users, counts.slots,
                    counts.meetings, properties.location(), Duration.ofNanos(System.nanoTime() - started).toMillis());
            return true;
        });
        return Boolean.TRUE.equals(seeded);
    }

    private Counts insert(SeedData data) {
        Instant now = timeRules.now();
        Counts counts = new Counts();

        // Users first, so participants can be linked to registered users by e-mail.
        Map<String, Long> userIds = new HashMap<>();
        List<Long> calendarIds = new ArrayList<>();
        for (SeedData.SeedUser seedUser : data.users()) {
            Instant createdAt = now.minus(Duration.ofDays(seedUser.createdDaysAgo()));
            User user = new User(seedUser.name().strip(), UserService.normalizeEmail(seedUser.email()), createdAt);
            Calendar calendar = new Calendar(user, zoneOf(seedUser), createdAt);
            entityManager.persist(user);
            entityManager.persist(calendar);
            userIds.put(user.getEmail(), user.getId());
            calendarIds.add(calendar.getId());
            counts.users++;
        }
        entityManager.flush();
        entityManager.clear();

        // Then each user's slots and meetings, flushing per user to keep the persistence context small.
        for (int i = 0; i < data.users().size(); i++) {
            SeedData.SeedUser seedUser = data.users().get(i);
            if (seedUser.slots() == null) {
                continue;
            }
            Calendar calendar = entityManager.getReference(Calendar.class, calendarIds.get(i));
            ZoneId zone = zoneOf(seedUser);
            LocalDate monday = LocalDate.ofInstant(now, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant userCreatedAt = now.minus(Duration.ofDays(seedUser.createdDaysAgo()));
            for (SeedData.SeedSlot seedSlot : seedUser.slots()) {
                Instant start = monday.plusDays(seedSlot.day()).atTime(seedSlot.start()).atZone(zone).toInstant();
                Duration duration = Duration.ofMinutes(seedSlot.minutes());
                timeRules.validateSlotDuration(duration);
                // Pretend the slot was published a week ahead (but not before its owner registered).
                Instant createdAt = latest(userCreatedAt, earliest(now, start).minus(Duration.ofDays(7)));
                TimeSlot slot = new TimeSlot(calendar, start, start.plus(duration), seedSlot.status(), createdAt);
                entityManager.persist(slot);
                counts.slots++;
                if (seedSlot.meeting() != null) {
                    persistMeeting(seedUser, seedSlot, slot, createdAt, userIds);
                    counts.meetings++;
                }
            }
            entityManager.flush();
            entityManager.clear();
        }
        return counts;
    }

    private void persistMeeting(SeedData.SeedUser seedUser, SeedData.SeedSlot seedSlot, TimeSlot slot,
                                Instant slotCreatedAt, Map<String, Long> userIds) {
        if (seedSlot.status() != SlotStatus.BUSY) {
            throw new IllegalStateException("Seed slot of %s on day %d at %s has a meeting but is not BUSY"
                    .formatted(seedUser.email(), seedSlot.day(), seedSlot.start()));
        }
        SeedData.SeedMeeting seedMeeting = seedSlot.meeting();
        Instant bookedAt = slotCreatedAt.plus(Duration.ofDays(1));
        Meeting meeting = new Meeting(slot, seedMeeting.title().strip(), seedMeeting.description(),
                participants(seedMeeting.participants(), userIds), bookedAt);
        if (Boolean.TRUE.equals(seedMeeting.updated())) {
            meeting.updateDetails(null, null, bookedAt.plus(Duration.ofHours(2)));
        }
        entityManager.persist(meeting);
    }

    /** Same rules as the API: e-mails are normalised and de-duplicated, registered users are linked. */
    private static List<Participant> participants(List<SeedData.SeedParticipant> requested, Map<String, Long> userIds) {
        if (requested == null) {
            return List.of();
        }
        Map<String, SeedData.SeedParticipant> byEmail = new LinkedHashMap<>();
        requested.forEach(p -> byEmail.putIfAbsent(UserService.normalizeEmail(p.email()), p));
        return byEmail.entrySet().stream()
                .map(e -> new Participant(e.getKey(), e.getValue().name(), userIds.get(e.getKey())))
                .toList();
    }

    private static ZoneId zoneOf(SeedData.SeedUser user) {
        return user.timeZone() == null ? ZoneId.of("UTC") : ZoneId.of(user.timeZone());
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static final class Counts {
        int users;
        int slots;
        int meetings;
    }
}
