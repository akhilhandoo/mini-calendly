package com.minicalendly.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends IntegrationTest {

    @Test
    void createsAndFetchesUser() throws Exception {
        long id = createUser("Alice@Example.com");

        getUrl("/api/v1/users/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.timeZone", is("Europe/Berlin")));
    }

    @Test
    void defaultsTimeZoneToUtc() throws Exception {
        postJson("/api/v1/users", """
                {"name": "Bob", "email": "bob@example.com"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone", is("UTC")));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() throws Exception {
        createUser("alice@example.com");

        postJson("/api/v1/users", """
                {"name": "Alice 2", "email": "ALICE@example.com"}""")
                .andExpect(status().isConflict());
    }

    @Test
    void validatesInput() throws Exception {
        postJson("/api/v1/users", """
                {"name": "", "email": "not-an-email"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());

        postJson("/api/v1/users", """
                {"name": "Zed", "email": "zed@example.com", "timeZone": "Mars/Olympus"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsUsersPaged() throws Exception {
        for (int i = 0; i < 3; i++) {
            createUser("user" + i + "@example.com");
        }

        getUrl("/api/v1/users?size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void unknownUserIs404() throws Exception {
        getUrl("/api/v1/users/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("User 999999 not found")))
                .andExpect(jsonPath("$.instance", is("/api/v1/users/999999")));
    }
}
