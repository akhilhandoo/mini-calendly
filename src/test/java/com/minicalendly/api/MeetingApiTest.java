package com.minicalendly.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeetingApiTest extends IntegrationTest {

    @Test
    void convertsFreeSlotIntoMeeting() throws Exception {
        long alice = createUser("alice@example.com");
        long bob = createUser("bob@example.com");
        long slot = createSlot(alice, at(9), 30);

        long meeting = idOf(scheduleMeeting(alice, slot, """
                [{"email": "BOB@example.com", "name": "Bob"},
                 {"email": "bob@example.com"},
                 {"email": "guest@external.org", "name": "Guest"}]""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId", is((int) alice)))
                .andExpect(jsonPath("$.startTime", is(at(9).toString())))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].userId", is((int) bob)))
                .andExpect(jsonPath("$.participants[1].userId", nullValue())));

        getUrl("/api/v1/users/%d/slots/%d".formatted(alice, slot))
                .andExpect(jsonPath("$.status", is("BUSY")))
                .andExpect(jsonPath("$.meetingId", is((int) meeting)));
    }

    @Test
    void cannotBookBusyOrAlreadyBookedSlot() throws Exception {
        long alice = createUser("alice@example.com");
        long slot = createSlot(alice, at(9), 30);
        scheduleMeeting(alice, slot, "[]").andExpect(status().isCreated());
        scheduleMeeting(alice, slot, "[]").andExpect(status().isConflict());

        long busy = createSlot(alice, at(10), 30);
        patchJson("/api/v1/users/%d/slots/%d".formatted(alice, busy), """
                {"status": "BUSY"}""").andExpect(status().isOk());
        scheduleMeeting(alice, busy, "[]").andExpect(status().isConflict());
    }

    @Test
    void cannotBookAnotherUsersSlot() throws Exception {
        long alice = createUser("alice@example.com");
        long bob = createUser("bob@example.com");
        long slot = createSlot(alice, at(9), 30);

        scheduleMeeting(bob, slot, "[]").andExpect(status().isNotFound());
    }

    @Test
    void validatesMeetingRequest() throws Exception {
        long alice = createUser("alice@example.com");
        long slot = createSlot(alice, at(9), 30);

        postJson("/api/v1/users/%d/meetings".formatted(alice), """
                {"slotId": %d, "title": " ", "participants": [{"email": "nope"}]}""".formatted(slot))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors['participants[0].email']").exists());
    }

    @Test
    void participantSeesMeetingButCannotModifyIt() throws Exception {
        long alice = createUser("alice@example.com");
        long bob = createUser("bob@example.com");
        long carol = createUser("carol@example.com");
        long meeting = idOf(scheduleMeeting(alice, createSlot(alice, at(9), 30), """
                [{"email": "bob@example.com"}]"""));
        String range = "from=%s&to=%s".formatted(at(0), at(24));

        getUrl("/api/v1/users/%d/meetings?%s".formatted(bob, range))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) meeting)));
        getUrl("/api/v1/users/%d/meetings/%d".formatted(bob, meeting)).andExpect(status().isOk());
        getUrl("/api/v1/users/%d/meetings/%d".formatted(carol, meeting)).andExpect(status().isNotFound());
        getUrl("/api/v1/users/%d/meetings?%s".formatted(carol, range)).andExpect(jsonPath("$.content", hasSize(0)));
        deleteUrl("/api/v1/users/%d/meetings/%d".formatted(bob, meeting)).andExpect(status().isNotFound());
    }

    @Test
    void listsMeetingsInRangeOrderedByStart() throws Exception {
        long alice = createUser("alice@example.com");
        scheduleMeeting(alice, createSlot(alice, at(14), 30), "[]");
        scheduleMeeting(alice, createSlot(alice, at(9), 30), "[]");
        scheduleMeeting(alice, createSlot(alice, at(30), 30), "[]");

        getUrl("/api/v1/users/%d/meetings?from=%s&to=%s".formatted(alice, at(0), at(24)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].startTime", is(at(9).toString())))
                .andExpect(jsonPath("$.content[1].startTime", is(at(14).toString())));
    }

    @Test
    void updatesMeetingDetails() throws Exception {
        long alice = createUser("alice@example.com");
        long meeting = idOf(scheduleMeeting(alice, createSlot(alice, at(9), 30), """
                [{"email": "bob@example.com"}]"""));

        patchJson("/api/v1/users/%d/meetings/%d".formatted(alice, meeting), """
                {"title": "Planning", "participants": [{"email": "carol@example.com"}, {"email": "dan@example.com"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Planning")))
                .andExpect(jsonPath("$.description", is("Weekly sync")))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].email", is("carol@example.com")));
    }

    @Test
    void cancellingMeetingFreesSlot() throws Exception {
        long alice = createUser("alice@example.com");
        long slot = createSlot(alice, at(9), 30);
        long meeting = idOf(scheduleMeeting(alice, slot, "[]"));

        deleteUrl("/api/v1/users/%d/meetings/%d".formatted(alice, meeting)).andExpect(status().isNoContent());

        getUrl("/api/v1/users/%d/slots/%d".formatted(alice, slot))
                .andExpect(jsonPath("$.status", is("FREE")))
                .andExpect(jsonPath("$.meetingId", nullValue()));
        scheduleMeeting(alice, slot, "[]").andExpect(status().isCreated());
    }
}
