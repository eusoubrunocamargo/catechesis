-- V2__church.sql
-- Creates the first tenant-root table. Every other table that carries
-- church_id will reference this one.
--
-- Conventions established here (followed by all subsequent tables):
--   - UUID primary keys (application-generated)
--   - TIMESTAMPTZ for all time columns
--   - snake_case, singular table names
--   - VARCHAR(N) with deliberate sizes
--   - created_at / updated_at on every mutable entity
--   - Church is NOT tenant-scoped (it IS the tenant), so no church_id

CREATE TABLE church (
    id           UUID         PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    timezone     VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
