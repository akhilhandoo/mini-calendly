# mini-calendly

An API-first meeting scheduling service. Users publish **time slots** in their personal calendar, mark them
**free or busy**, convert free slots into **meetings** with participants, and query an **aggregated free/busy
view**. They can also look for time when several users are all free.

Tech stack: Java 24, Spring Boot 4.1, Spring Data JPA (Hibernate 7), PostgreSQL 17, Flyway, springdoc-openapi,
Micrometer/Prometheus, JUnit 5 + Testcontainers, Gradle (Kotlin DSL).

---

## Quick start

Requirements: Docker with the Compose plugin. A JDK isn't needed to run the service.

```bash
docker compose up --build -d
```

| What                 | URL                                          |
|----------------------|----------------------------------------------|
| REST API             | http://localhost:8080/api/v1                 |
| Swagger UI           | http://localhost:8080/swagger-ui.html        |
| OpenAPI spec (JSON)  | http://localhost:8080/v3/api-docs            |
| Health / readiness   | http://localhost:8080/actuator/health        |
| Prometheus metrics   | http://localhost:8080/actuator/prometheus    |
| Prometheus UI        | http://localhost:9090                        |

Stop with `docker compose down` (add `-v` to also drop the database volume).

The compose file starts **postgres** (with a health check), **app** (built from the multi-stage `Dockerfile`,
waits until postgres is healthy and migrates the schema on startup), and **prometheus** (scrapes the app).

> **macOS without Docker Desktop:** `brew install docker docker-compose colima && colima start` works too.
> To make the compose plugin visible, add `"cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"]`
> to `~/.docker/config.json`.

---

## Using the API

All timestamps are ISO-8601. Requests can use any offset (`2030-01-15T10:00:00+01:00`); responses are always
in UTC (`...Z`). In query strings, use `Z` or URL-encode `+` as `%2B`.

### Resources

| Method | Path                                              | Purpose                                                        |
|--------|---------------------------------------------------|----------------------------------------------------------------|
| POST   | `/api/v1/users`                                   | Register a user (a personal calendar is created automatically) |
| GET    | `/api/v1/users/{userId}`                          | Get a user                                                     |
| GET    | `/api/v1/users?page=&size=`                       | List users (paged)                                             |
| POST   | `/api/v1/users/{userId}/slots`                    | Create one slot (`endTime` **or** `durationMinutes`)           |
| POST   | `/api/v1/users/{userId}/slots/batch`              | Split a range into consecutive slots of a configurable length  |
| GET    | `/api/v1/users/{userId}/slots?from=&to=&status=`  | List slots in a window (paged, ordered by start)               |
| GET    | `/api/v1/users/{userId}/slots/{slotId}`           | Get a slot (includes `meetingId` if booked)                    |
| PATCH  | `/api/v1/users/{userId}/slots/{slotId}`           | Move/resize a slot and/or mark it `FREE` / `BUSY`              |
| DELETE | `/api/v1/users/{userId}/slots/{slotId}`           | Delete a slot                                                  |
| POST   | `/api/v1/users/{userId}/meetings`                 | Convert a FREE slot into a meeting                             |
| GET    | `/api/v1/users/{userId}/meetings?from=&to=`       | Meetings the user organises **or** participates in (paged)     |
| GET    | `/api/v1/users/{userId}/meetings/{meetingId}`     | Get a meeting (organiser or participant)                       |
| PATCH  | `/api/v1/users/{userId}/meetings/{meetingId}`     | Update title / description / participants (organiser only)     |
| DELETE | `/api/v1/users/{userId}/meetings/{meetingId}`     | Cancel the meeting; its slot becomes FREE again                |
| GET    | `/api/v1/users/{userId}/availability?from=&to=`   | Aggregated free/busy view                                      |
| GET    | `/api/v1/availability/common?userIds=1,2&from=&to=` | Windows in which **all** given users are free                |

Full request/response schemas are in Swagger UI.

### Walk-through

```bash
API=http://localhost:8080/api/v1

# 1. Two users
curl -s -XPOST $API/users -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","timeZone":"Europe/Berlin"}'
curl -s -XPOST $API/users -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com"}'

# 2. Alice opens 30-minute slots from 09:00 to 12:00 (6 slots)
curl -s -XPOST $API/users/1/slots/batch -H 'Content-Type: application/json' \
  -d '{"from":"2030-01-15T09:00:00Z","to":"2030-01-15T12:00:00Z","slotDurationMinutes":30}'

# ...or a single slot
curl -s -XPOST $API/users/1/slots -H 'Content-Type: application/json' \
  -d '{"startTime":"2030-01-15T14:00:00Z","durationMinutes":60}'

# 3. Block a slot without a meeting (mark busy), or move it
curl -s -XPATCH $API/users/1/slots/2 -H 'Content-Type: application/json' -d '{"status":"BUSY"}'
curl -s -XPATCH $API/users/1/slots/7 -H 'Content-Type: application/json' -d '{"startTime":"2030-01-15T15:00:00Z"}'

# 4. Convert a free slot into a meeting
curl -s -XPOST $API/users/1/meetings -H 'Content-Type: application/json' -d '{
  "slotId": 1,
  "title": "Kickoff",
  "description": "Project kickoff",
  "participants": [{"email":"bob@example.com","name":"Bob"}, {"email":"guest@external.org"}]
}'

# 5. Aggregated free/busy view for the day
curl -s "$API/users/1/availability?from=2030-01-15T00:00:00Z&to=2030-01-16T00:00:00Z"
# {"userId":1,"free":[{"start":"2030-01-15T09:30:00Z","end":"2030-01-15T10:00:00Z",...}, ...],
#  "busy":[...], "freeMinutes":..., "busyMinutes":...}

# 6. Bob sees the meeting he was invited to
curl -s "$API/users/2/meetings?from=2030-01-15T00:00:00Z&to=2030-01-16T00:00:00Z"

# 7. When are Alice and Bob both free?
curl -s "$API/availability/common?userIds=1,2&from=2030-01-15T00:00:00Z&to=2030-01-16T00:00:00Z"

# 8. Cancel the meeting (slot becomes FREE again)
curl -s -XDELETE $API/users/1/meetings/1
```

### Business rules

* Slots of **one user** never overlap. Adjacent slots (one ends at 10:00, the next starts at 10:00) are fine.
  Different users' slots are independent.
* Slot duration must be between `calendar.slots.min-duration` (default 5 min) and `max-duration` (default 8 h).
* Slots can't start in the past (`calendar.slots.allow-past=false`).
* Batch creation produces at most `max-batch-size` (500) slots and is **all-or-nothing**. A remainder shorter
  than one slot at the end of the range is ignored.
* Only a `FREE` slot can be converted into a meeting, and it becomes `BUSY`. A slot can hold at most one
  meeting.
* A booked slot can't be moved, freed or deleted. Cancel the meeting first.
* Participants are identified by e-mail and de-duplicated case-insensitively. If an e-mail belongs to a
  registered user, the participant is linked (`userId`) and the meeting appears in that user's meeting list.
* Query windows (`from`/`to`) are half-open `[from, to)` and limited to `calendar.query.max-range` (93 days).
  Common availability compares at most 20 users.
* **Availability semantics:** `free` and `busy` contain the merged `FREE` and `BUSY` slots, clipped to the
  window. Time that no slot covers is neither free nor busy; it is simply not offered.

### Errors

Errors use RFC 9457 *problem details* (`application/problem+json`):

```json
{"status":409,"title":"Conflict","detail":"Slot 1 is already booked","instance":"/api/v1/users/1/meetings"}
```

| Status | When                                                                          |
|--------|-------------------------------------------------------------------------------|
| 400    | Bean validation failures (with an `errors` map per field) or rule violations  |
| 404    | Unknown user / slot / meeting, or one that belongs to another user           |
| 409    | Overlapping slot, double booking, booked slot modification, duplicate e-mail  |

---

## Design

### Domain model

```
User 1───1 Calendar 1───* TimeSlot 1───0..1 Meeting 1───* Participant
```

* **Calendar** exists only in the domain and persistence layers. Every user gets exactly one, and the API
  reaches it through `/users/{userId}/...`. It owns the user's time zone and is the consistency boundary for
  that user's slots.
* **TimeSlot** has `start_time`, `end_time` (`timestamptz`) and `status ∈ {FREE, BUSY}`. `BUSY` covers both
  "blocked by the owner" and "occupied by a meeting"; the `meetingId` field tells the two apart.
* **Meeting** references its slot through a unique foreign key, so at most one meeting can exist per slot.

### Consistency under concurrency

Every write to a user's slots and meetings first takes a **row lock on that user's calendar**
(`SELECT ... FOR UPDATE`). Writes for the same user run one at a time, and different users never contend.
The database adds its own safety nets:

* an `EXCLUDE USING gist (calendar_id WITH =, tstzrange(start_time, end_time) WITH &&)` constraint makes
  overlapping slots impossible at the storage level;
* `UNIQUE(meeting.slot_id)` prevents double booking;
* `@Version` columns guard against lost updates.

Constraint violations map to `409`. `ConcurrencyTest` fires 16 parallel requests at the same slot or time
range and checks that exactly one succeeds.

### Performance and scale

The target is hundreds of users with thousands of slots each.

* **Indexes:** `(calendar_id, start_time)` serves every range query; the GiST index from the exclusion
  constraint serves overlap checks; participant lookups use `meeting_participant(user_id)`.
* **Range queries only:** every list endpoint requires a bounded `from`/`to` window and is paged (default 50,
  max 500).
* **Projection for aggregation:** free/busy reads fetch only `(start, end, status)` tuples, not entities, and
  merge them in a single linear pass (`AvailabilityAggregator`). Common availability uses **one** query for
  all users plus a linear k-way intersection.
* **Batch writes:** sequence IDs are pre-allocated in blocks of 50, Hibernate JDBC batching is enabled, and
  the driver uses `reWriteBatchedInserts`, so a 500-slot batch goes out in a handful of round trips.
* **No N+1:** list queries `join fetch` the slot and calendar; collections use `default_batch_fetch_size`.
  Open-session-in-view is disabled.
* **Virtual threads** serve requests, and the HikariCP pool size is configurable (`DB_POOL_SIZE`).
* The service is stateless, so it scales horizontally behind a load balancer. Correctness relies only on
  PostgreSQL locks and constraints.

### Project layout

```
src/main/java/com/minicalendly
├── api/            REST controllers, DTOs (records), problem-detail exception handler
├── service/        Use cases, business rules (TimeRules), pure interval math (AvailabilityAggregator)
├── repository/     Spring Data repositories and query projections
├── domain/         JPA entities: User, Calendar, TimeSlot, Meeting, Participant
└── config/         Typed configuration (CalendarProperties), clock, OpenAPI
src/main/resources/db/migration   Flyway migrations
```

---

## Configuration

| Property / env var                                   | Default                                      |
|------------------------------------------------------|----------------------------------------------|
| `DB_URL`                                             | `jdbc:postgresql://localhost:5432/calendly?reWriteBatchedInserts=true` |
| `DB_USER` / `DB_PASSWORD`                            | `calendly` / `calendly`                      |
| `DB_POOL_SIZE`                                       | `20`                                         |
| `calendar.slots.min-duration` / `max-duration`       | `5m` / `8h`                                  |
| `calendar.slots.max-batch-size`                      | `500`                                        |
| `calendar.slots.allow-past`                          | `false`                                      |
| `calendar.query.max-range`                           | `93d`                                        |
| `calendar.query.max-users-for-common-availability`   | `20`                                         |
| `calendar.seed.enabled` (`CALENDAR_SEED_ENABLED`)    | `true`                                       |

Any property can be overridden with an environment variable, e.g. `CALENDAR_SLOTS_MAX_BATCH_SIZE=1000`.

## Demo data

On startup, `SeedDataLoader` fills an **empty** database from `src/main/resources/seed/seed-data.json`. If any
user already exists, it does nothing, so restarts never duplicate data. Turn it off with
`CALENDAR_SEED_ENABLED=false`. To reload from scratch, run `docker compose down -v && docker compose up -d`.

The data set has 100 users, about 14,500 slots and about 7,300 meetings. The slots run from 4 weeks back to
8 weeks ahead. Times in the file are relative: a day offset from this week's Monday plus a local time in the
user's time zone. The calendars therefore always look current, and working hours stay in local time.
Regenerate the file with `python3 scripts/generate_seed_data.py` (output is deterministic).

Users in 12 teams across many time zones have different working styles. Some are packed, some
book consultant-style hourly slots, some work flexible hours and some are light users. A few have no slots.
The named users below cover specific scenarios (all `@acme.example.com`):

| User                                  | Scenario                                                                 |
|---------------------------------------|--------------------------------------------------------------------------|
| `ada.lovelace` (Berlin)               | Back-to-back 30-min slots 09-17, all busy: merges into one busy interval |
| `grace.hopper` (New York)             | Only free slots, no meetings                                             |
| `alan.turing`                         | Registered without a time zone (defaults to UTC), no slots               |
| `edsger.dijkstra` (Amsterdam)         | Edge cases: 5-min and 8-h slots, FREE→BUSY→FREE touching, slot ending at midnight |
| `barbara.liskov` (Los Angeles)        | Owner-blocked `BUSY` time (focus, lunch) without meetings                |
| `linus.torvalds` (Helsinki)           | No slots of his own, but a participant in many meetings                  |
| `margaret.hamilton` (Chicago)         | Large meetings: registered and external participants, unnamed participants, duplicate e-mail in different case |
| `srinivasa.ramanujan`, `pemba.sherpa` | Half- and quarter-hour UTC offsets (Kolkata, Kathmandu)                  |
| `klaus.becker`, `zoe.mueller`, `lea.dubois` | Common availability: Tuesdays 14:30-15:30 overlap, Thursdays only touch (no common time) |
| `kenji.watanabe` (Tokyo)              | Evening and weekend slots                                                |
| `nora.newcomer`                       | Registered today, nothing scheduled                                      |

Meetings also cover past and future dates, missing descriptions, no participants, external-only guests and
edited details (`updatedAt` > `createdAt`). `SeedDataApiTest` loads the file and checks these scenarios.

## Metrics

On top of the standard JVM, HikariCP and `http_server_requests_seconds` metrics (with histograms):

| Metric                                                   | Meaning                               |
|----------------------------------------------------------|---------------------------------------|
| `calendar_slots_added_total`                             | Slots created (single + batch)        |
| `calendar_meetings_scheduled_total`                      | Free slots converted into meetings    |
| `calendar_meetings_cancelled_total`                      | Meetings cancelled                    |
| `calendar_availability_query_seconds{type="user/common"}`| Free/busy aggregation latency         |

Example PromQL: `rate(calendar_meetings_scheduled_total[5m])`, or
`histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`.

---

## Running locally without Compose

```bash
docker compose up -d postgres     # or any PostgreSQL 14+ with the btree_gist extension available
./gradlew bootRun                 # needs JDK 24 or newer
```

## Tests

```bash
./gradlew test
```

* `AvailabilityAggregatorTest`: unit tests for merging, clipping and intersecting intervals.
* `UserApiTest`, `SlotApiTest`, `MeetingApiTest`, `AvailabilityApiTest`: end-to-end API tests through
  MockMvc against a **real PostgreSQL 17** started by Testcontainers, so the Flyway migration, the exclusion
  constraint and the locking run exactly as in production.
* `ConcurrencyTest`: parallel double-booking and overlapping-slot races.

Testcontainers needs a running Docker daemon. With Colima, export:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Out of scope / next steps

* **Authentication and authorisation:** the user id in the path is trusted. In production it would come
  from a token (e.g. Spring Security + OAuth2 resource server).
* Booking a meeting doesn't block the participants' own calendars, and there are no notifications or
  invitations.
* Recurring slots (RRULE), and a read cache for hot availability windows if read traffic grows by orders of
  magnitude.
