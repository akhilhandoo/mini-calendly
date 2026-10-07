-- btree_gist lets the exclusion constraint below combine "=" on a bigint with "&&" on a range.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SEQUENCE app_user_seq INCREMENT BY 50;
CREATE SEQUENCE calendar_seq INCREMENT BY 50;
CREATE SEQUENCE time_slot_seq INCREMENT BY 50;
CREATE SEQUENCE meeting_seq INCREMENT BY 50;

CREATE TABLE app_user
(
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    email      VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- Every user owns exactly one calendar. The calendar is a domain concept only;
-- the API addresses it implicitly through the owning user.
CREATE TABLE calendar
(
    id         BIGINT PRIMARY KEY,
    owner_id   BIGINT      NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    time_zone  VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE time_slot
(
    id          BIGINT PRIMARY KEY,
    calendar_id BIGINT      NOT NULL REFERENCES calendar (id) ON DELETE CASCADE,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(16) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_time_slot_range CHECK (end_time > start_time),
    CONSTRAINT ck_time_slot_status CHECK (status IN ('FREE', 'BUSY')),
    -- Database-level guarantee that slots of one calendar never overlap,
    -- even if two writers race past the application-level check.
    CONSTRAINT ex_time_slot_no_overlap EXCLUDE USING gist (
        calendar_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    )
);
-- Range scans ("slots of calendar X between A and B") are the hottest query.
CREATE INDEX ix_time_slot_calendar_start ON time_slot (calendar_id, start_time);

CREATE TABLE meeting
(
    id          BIGINT PRIMARY KEY,
    slot_id     BIGINT        NOT NULL UNIQUE REFERENCES time_slot (id),
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(4000),
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE meeting_participant
(
    meeting_id BIGINT       NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    email      VARCHAR(320) NOT NULL,
    name       VARCHAR(200),
    user_id    BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (meeting_id, email)
);
CREATE INDEX ix_meeting_participant_user ON meeting_participant (user_id);
