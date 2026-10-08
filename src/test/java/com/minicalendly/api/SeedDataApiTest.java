package com.minicalendly.api;

import com.jayway.jsonpath.JsonPath;
import com.minicalendly.seed.SeedDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Loads the bundled demo data and checks the scenarios it promises (see README "Demo data"). */
class SeedDataApiTest extends IntegrationTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired
    private SeedDataLoader seedDataLoader;

    @BeforeEach
    void seed() throws Exception {
        assertThat(seedDataLoader.seed()).isTrue();
    }

    @Test
    void seedsOnlyAnEmptyDatabase() throws Exception {
        assertThat(seedDataLoader.seed()).isFalse();
        getUrl("/api/v1/users?size=1").andExpect(jsonPath("$.totalElements", is(100)));
    }

    @Test
    void userWithoutTimeZoneDefaultsToUtcAndHasNoSlots() throws Exception {
        long alan = userId("alan.turing@acme.example.com");
        getUrl("/api/v1/users/%d".formatted(alan)).andExpect(jsonPath("$.timeZone", is("UTC")));
        getUrl("/api/v1/users/%d/availability?from=%s&to=%s".formatted(alan, Instant.now(), Instant.now().plus(Duration.ofDays(60))))
                .andExpect(jsonPath("$.free", empty()))
                .andExpect(jsonPath("$.busy", empty()));
    }

    @Test
    void backToBackBusySlotsMergeIntoOneInterval() throws Exception {
        long ada = userId("ada.lovelace@acme.example.com");
        getUrl("/api/v1/users/%d/availability?from=%s&to=%s".formatted(ada, berlin(7, 0, 0), berlin(8, 0, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free", empty()))
                .andExpect(jsonPath("$.busy", hasSize(1)))
                .andExpect(jsonPath("$.busy[0].start", is(berlin(7, 9, 0).toString())))
                .andExpect(jsonPath("$.busyMinutes", is(480)));
    }

    @Test
    void commonFreeTimeOfThreeColleagues() throws Exception {
        String ids = "%d,%d,%d".formatted(userId("klaus.becker@acme.example.com"),
                userId("zoe.mueller@acme.example.com"), userId("lea.dubois@acme.example.com"));
        // Next Tuesday the free slots overlap between 14:30 and 15:30.
        getUrl("/api/v1/availability/common?userIds=%s&from=%s&to=%s".formatted(ids, berlin(8, 12, 0), berlin(8, 16, 0)))
                .andExpect(jsonPath("$.free", hasSize(1)))
                .andExpect(jsonPath("$.free[0].start", is(berlin(8, 14, 30).toString())))
                .andExpect(jsonPath("$.free[0].end", is(berlin(8, 15, 30).toString())));
        // Next Thursday they only touch, so there is no common free time.
        getUrl("/api/v1/availability/common?userIds=%s&from=%s&to=%s".formatted(ids, berlin(10, 9, 0), berlin(10, 13, 0)))
                .andExpect(jsonPath("$.free", empty()));
    }

    @Test
    void participantSeesMeetingsWithoutOwningSlots() throws Exception {
        long linus = userId("linus.torvalds@acme.example.com");
        Instant now = Instant.now();
        String range = "from=%s&to=%s".formatted(now.minus(Duration.ofDays(35)), now.plus(Duration.ofDays(58)));
        getUrl("/api/v1/users/%d/slots?%s".formatted(linus, range)).andExpect(jsonPath("$.totalElements", is(0)));
        getUrl("/api/v1/users/%d/meetings?%s".formatted(linus, range))
                .andExpect(jsonPath("$.totalElements", greaterThan(10)));
    }

    @Test
    void participantsAreDeduplicatedAndLinked() throws Exception {
        long margaret = userId("margaret.hamilton@acme.example.com");
        getUrl("/api/v1/users/%d/meetings?from=%s&to=%s".formatted(margaret, berlin(8, 0, 0), berlin(9, 0, 0)))
                .andExpect(jsonPath("$.content[?(@.title == 'All-hands')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.title == 'All-hands')].participants[?(@.email == 'linus.torvalds@acme.example.com')].userId",
                        hasSize(1)));
    }

    /** Local time in Berlin, {@code day} days after the Monday of the current week. */
    private static Instant berlin(int day, int hour, int minute) {
        LocalDate monday = LocalDate.now(BERLIN).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusDays(day).atTime(LocalTime.of(hour, minute)).atZone(BERLIN).toInstant();
    }

    private long userId(String email) throws Exception {
        String json = getUrl("/api/v1/users?size=100").andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$.content[?(@.email == '%s')].id".formatted(email));
        assertThat(ids).hasSize(1);
        return ids.getFirst().longValue();
    }
}
