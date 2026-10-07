package com.minicalendly.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvailabilityApiTest extends IntegrationTest {

    @Test
    void aggregatesFreeAndBusyIntervals() throws Exception {
        long alice = createUser("alice@example.com");
        postJson("/api/v1/users/%d/slots/batch".formatted(alice), """
                {"from": "%s", "to": "%s", "slotDurationMinutes": 30}""".formatted(at(9), at(12)))
                .andExpect(status().isCreated());
        long booked = idOf(getUrl("/api/v1/users/%d/slots?from=%s&to=%s&size=1&page=2"
                .formatted(alice, at(0), at(24))).andExpect(status().isOk()), "$.content[0].id");
        scheduleMeeting(alice, booked, "[]").andExpect(status().isCreated());
        createSlot(alice, at(14), 60);

        getUrl("/api/v1/users/%d/availability?from=%s&to=%s".formatted(alice, at(0), at(24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free", hasSize(3)))
                .andExpect(jsonPath("$.free[0].start", is(at(9).toString())))
                .andExpect(jsonPath("$.free[0].end", is(at(10).toString())))
                .andExpect(jsonPath("$.free[1].start", is(at(10.5).toString())))
                .andExpect(jsonPath("$.free[1].end", is(at(12).toString())))
                .andExpect(jsonPath("$.free[2].durationMinutes", is(60)))
                .andExpect(jsonPath("$.busy", hasSize(1)))
                .andExpect(jsonPath("$.busy[0].start", is(at(10).toString())))
                .andExpect(jsonPath("$.freeMinutes", is(210)))
                .andExpect(jsonPath("$.busyMinutes", is(30)));
    }

    @Test
    void clipsToRequestedWindow() throws Exception {
        long alice = createUser("alice@example.com");
        createSlot(alice, at(9), 120);

        getUrl("/api/v1/users/%d/availability?from=%s&to=%s".formatted(alice, at(10), at(10.5)))
                .andExpect(jsonPath("$.free[0].start", is(at(10).toString())))
                .andExpect(jsonPath("$.free[0].end", is(at(10.5).toString())))
                .andExpect(jsonPath("$.freeMinutes", is(30)));
    }

    @Test
    void findsCommonFreeTime() throws Exception {
        long alice = createUser("alice@example.com");
        long bob = createUser("bob@example.com");
        createSlot(alice, at(9), 180);
        createSlot(bob, at(10), 60);
        createSlot(bob, at(11), 120);
        long bobBusy = createSlot(bob, at(8), 60);
        patchJson("/api/v1/users/%d/slots/%d".formatted(bob, bobBusy), """
                {"status": "BUSY"}""");

        getUrl("/api/v1/availability/common?userIds=%d,%d&from=%s&to=%s".formatted(alice, bob, at(0), at(24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free", hasSize(1)))
                .andExpect(jsonPath("$.free[0].start", is(at(10).toString())))
                .andExpect(jsonPath("$.free[0].end", is(at(12).toString())))
                .andExpect(jsonPath("$.freeMinutes", is(120)));
    }

    @Test
    void commonAvailabilityRequiresKnownUsers() throws Exception {
        long alice = createUser("alice@example.com");
        getUrl("/api/v1/availability/common?userIds=%d,999999&from=%s&to=%s".formatted(alice, at(0), at(24)))
                .andExpect(status().isNotFound());
    }

    private static long idOf(org.springframework.test.web.servlet.ResultActions result, String path) throws Exception {
        Number id = com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return id.longValue();
    }
}
