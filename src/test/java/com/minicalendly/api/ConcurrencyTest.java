package com.minicalendly.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.is;

/** Races many requests against the same calendar and checks that invariants hold. */
class ConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 16;

    @Test
    void slotCanBeBookedOnlyOnce() throws Exception {
        long alice = createUser("alice@example.com");
        long slot = createSlot(alice, at(9), 30);

        List<Integer> statuses = race(() -> scheduleMeeting(alice, slot, "[]").andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(THREADS - 1);
    }

    @Test
    void overlappingSlotsCannotBeCreatedConcurrently() throws Exception {
        long alice = createUser("alice@example.com");
        Instant start = at(9);

        List<Integer> statuses = race(() -> postJson("/api/v1/users/%d/slots".formatted(alice), """
                {"startTime": "%s", "durationMinutes": 30}""".formatted(start)).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        getUrl("/api/v1/users/%d/slots?from=%s&to=%s".formatted(alice, at(0), at(24)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    private static List<Integer> race(Callable<Integer> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
    }
}
