-- Campus Event Management System - SQLite schema
-- Applied automatically on startup (CREATE ... IF NOT EXISTS, safe to re-run).

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('student', 'admin')),
    student_class TEXT,
    passing_year  INTEGER,
    roll_no       TEXT
);

CREATE TABLE IF NOT EXISTS events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    description TEXT NOT NULL,
    category    TEXT NOT NULL CHECK (category IN ('Technical', 'Cultural', 'Sports', 'Workshop')),
    capacity    INTEGER NOT NULL CHECK (capacity >= 1),
    event_date  TEXT NOT NULL,
    event_time  TEXT NOT NULL,
    venue       TEXT NOT NULL
);

-- One event per venue, date and time slot (database-level safety net).
CREATE UNIQUE INDEX IF NOT EXISTS ux_event_slot
    ON events (event_date, event_time, lower(trim(venue)));

CREATE TABLE IF NOT EXISTS registrations (
    event_id      INTEGER NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id       INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    registered_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS ix_reg_user ON registrations (user_id);
