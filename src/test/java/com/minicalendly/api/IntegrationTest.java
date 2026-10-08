package com.minicalendly.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full Spring context against a real PostgreSQL (Testcontainers).
 * The container is shared by all test classes; tables are truncated after every test.
 * Demo seed data is disabled so that every test starts from an empty database.
 */
@SpringBootTest(properties = "calendar.seed.enabled=false")
@AutoConfigureMockMvc
abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    /** A day comfortably in the future so "no past slots" never interferes. */
    protected static final Instant DAY = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(30));

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE meeting_participant, meeting, time_slot, calendar, app_user CASCADE");
    }

    protected static Instant at(double hour) {
        return DAY.plus(Duration.ofMinutes((long) (hour * 60)));
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchJson(String url, String body) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getUrl(String url) throws Exception {
        return mvc.perform(get(url));
    }

    protected ResultActions deleteUrl(String url) throws Exception {
        return mvc.perform(delete(url));
    }

    protected long createUser(String email) throws Exception {
        String body = """
                {"name": "%s", "email": "%s", "timeZone": "Europe/Berlin"}""".formatted(email, email);
        return idOf(postJson("/api/v1/users", body).andExpect(status().isCreated()));
    }

    protected long createSlot(long userId, Instant start, int minutes) throws Exception {
        String body = """
                {"startTime": "%s", "durationMinutes": %d}""".formatted(start, minutes);
        return idOf(postJson("/api/v1/users/%d/slots".formatted(userId), body).andExpect(status().isCreated()));
    }

    protected ResultActions scheduleMeeting(long userId, long slotId, String participantsJson) throws Exception {
        String body = """
                {"slotId": %d, "title": "Sync", "description": "Weekly sync", "participants": %s}"""
                .formatted(slotId, participantsJson);
        return postJson("/api/v1/users/%d/meetings".formatted(userId), body);
    }

    protected static long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
