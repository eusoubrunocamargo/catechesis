-- V1__baseline.sql
-- Proves the Flyway migration pipeline works. The real domain tables
-- arrive in V2+ during Sprint 1.

CREATE TABLE _baseline_marker (
                                  id         UUID         PRIMARY KEY,
                                  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                  note       VARCHAR(200) NOT NULL
);

INSERT INTO _baseline_marker (id, note)
VALUES ('00000000-0000-0000-0000-000000000001', 'baseline — Sprint 0 scaffold');