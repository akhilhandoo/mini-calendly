package com.minicalendly.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SlotApiTest extends IntegrationTest {

    @Test
    void createsSlotWithDurationOrEndTime() throws Exception {
        long user = createUser("alice@example.com");

        postJson("/api/v1/users/%d/slots".formatted(user), """
                {"startTime": "%s", "durationMinutes": 30}""".formatted(at(9)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endTime", is(at(9.5).toString())))
                .andExpect(jsonPath("$.status", is("FREE")))
                .andExpect(jsonPath("$.userId", is((int) user)));

        postJson("/api/v1/users/%d/slots".formatted(user), """
                {"startTime": "%s", "endTime": "%s", "status": "BUSY"}""".formatted(at(10), at(11)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes", is(60)))
                .andExpect(jsonPath("$.status", is("BUSY")));
    }

    @Test
    void acceptsOffsetTimestamps() throws Exception {
        long user = createUser("alice@example.com");
        String local = at(9).atOffset(java.time.ZoneOffset.ofHours(2)).toString();

        postJson("/api/v1/users/%d/slots".formatted(user), """
                {"startTime": "%s", "durationMinutes": 30}""".formatted(local))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime", is(at(9).toString())));
    }

    @Test
    void rejectsInvalidSlots() throws Exception {
        long user = createUser("alice@example.com");
        String url = "/api/v1/users/%d/slots".formatted(user);

        postJson(url, """
                {"startTime": "%s", "endTime": "%s", "durationMinutes": 30}""".formatted(at(9), at(10)))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"startTime": "%s"}""".formatted(at(9)))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"startTime": "%s", "durationMinutes": 2}""".formatted(at(9)))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"startTime": "%s", "durationMinutes": 600}""".formatted(at(9)))
                .andExpect(status().isBadRequest());
        postJson(url, """
                {"startTime": "2000-01-01T09:00:00Z", "durationMinutes": 30}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Slots cannot start in the past")));
    }

    @Test
    void rejectsOverlappingSlotsButAllowsAdjacentOnes() throws Exception {
        long user = createUser("alice@example.com");
        createSlot(user, at(9), 60);

        postJson("/api/v1/users/%d/slots".formatted(user), """
                {"startTime": "%s", "durationMinutes": 30}""".formatted(at(9.5)))
                .andExpect(status().isConflict());
        createSlot(user, at(10), 30);
        createSlot(user, at(8.5), 30);
    }

    @Test
    void slotsOfDifferentUsersMayOverlap() throws Exception {
        createSlot(createUser("alice@example.com"), at(9), 60);
        createSlot(createUser("bob@example.com"), at(9), 60);
    }

    @Test
    void createsBatchOfConsecutiveSlots() throws Exception {
        long user = createUser("alice@example.com");

        postJson("/api/v1/users/%d/slots/batch".formatted(user), """
                {"from": "%s", "to": "%s", "slotDurationMinutes": 30}""".formatted(at(9), at(12.25)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[5].endTime", is(at(12).toString())));
    }

    @Test
    void batchIsAllOrNothing() throws Exception {
        long user = createUser("alice@example.com");
        createSlot(user, at(11), 30);

        postJson("/api/v1/users/%d/slots/batch".formatted(user), """
                {"from": "%s", "to": "%s", "slotDurationMinutes": 30}""".formatted(at(9), at(12)))
                .andExpect(status().isConflict());
        getUrl("/api/v1/users/%d/slots?from=%s&to=%s".formatted(user, at(0), at(24)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchesSlotsByRangeAndStatusWithPaging() throws Exception {
        long user = createUser("alice@example.com");
        postJson("/api/v1/users/%d/slots/batch".formatted(user), """
                {"from": "%s", "to": "%s", "slotDurationMinutes": 60}""".formatted(at(8), at(18)))
                .andExpect(status().isCreated());
        long busy = createSlot(user, at(19), 60);
        patchJson("/api/v1/users/%d/slots/%d".formatted(user, busy), """
                {"status": "BUSY"}""").andExpect(status().isOk());

        getUrl("/api/v1/users/%d/slots?from=%s&to=%s&size=4&page=1".formatted(user, at(9.5), at(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(10)))
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[0].startTime", is(at(13).toString())));
        getUrl("/api/v1/users/%d/slots?from=%s&to=%s&status=BUSY".formatted(user, at(0), at(24)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) busy)));
    }

    @Test
    void rejectsTooLargeQueryRange() throws Exception {
        long user = createUser("alice@example.com");
        getUrl("/api/v1/users/%d/slots?from=%s&to=%s".formatted(user, at(0), at(24 * 200)))
                .andExpect(status().isBadRequest());
        getUrl("/api/v1/users/%d/slots?from=%s&to=%s".formatted(user, at(10), at(9)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movesAndResizesSlot() throws Exception {
        long user = createUser("alice@example.com");
        long slot = createSlot(user, at(9), 30);
        createSlot(user, at(11), 30);
        String url = "/api/v1/users/%d/slots/%d".formatted(user, slot);

        patchJson(url, """
                {"startTime": "%s"}""".formatted(at(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime", is(at(10.5).toString())));
        patchJson(url, """
                {"durationMinutes": 60}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime", is(at(11).toString())));
        patchJson(url, """
                {"durationMinutes": 90}""")
                .andExpect(status().isConflict());
    }

    @Test
    void marksSlotBusyAndFree() throws Exception {
        long user = createUser("alice@example.com");
        long slot = createSlot(user, at(9), 30);
        String url = "/api/v1/users/%d/slots/%d".formatted(user, slot);

        patchJson(url, """
                {"status": "BUSY"}""").andExpect(jsonPath("$.status", is("BUSY")));
        patchJson(url, """
                {"status": "FREE"}""").andExpect(jsonPath("$.status", is("FREE")));
    }

    @Test
    void deletesSlot() throws Exception {
        long user = createUser("alice@example.com");
        long slot = createSlot(user, at(9), 30);

        deleteUrl("/api/v1/users/%d/slots/%d".formatted(user, slot)).andExpect(status().isNoContent());
        getUrl("/api/v1/users/%d/slots/%d".formatted(user, slot)).andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersSlot() throws Exception {
        long alice = createUser("alice@example.com");
        long bob = createUser("bob@example.com");
        long slot = createSlot(alice, at(9), 30);

        getUrl("/api/v1/users/%d/slots/%d".formatted(bob, slot)).andExpect(status().isNotFound());
        deleteUrl("/api/v1/users/%d/slots/%d".formatted(bob, slot)).andExpect(status().isNotFound());
    }

    @Test
    void bookedSlotCannotBeMovedFreedOrDeleted() throws Exception {
        long user = createUser("alice@example.com");
        long slot = createSlot(user, at(9), 30);
        scheduleMeeting(user, slot, "[]").andExpect(status().isCreated());
        String url = "/api/v1/users/%d/slots/%d".formatted(user, slot);

        patchJson(url, """
                {"startTime": "%s"}""".formatted(at(10))).andExpect(status().isConflict());
        patchJson(url, """
                {"status": "FREE"}""").andExpect(status().isConflict());
        deleteUrl(url).andExpect(status().isConflict());
    }
}
