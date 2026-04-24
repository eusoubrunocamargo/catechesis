-- V3__super_admin_and_catechist.sql
--
-- Introduces the two user tables:
--
--   - super_admin: system-level operators (one or two rows ever).
--                  Not tenant-scoped.
--
--   - catechist:   tenant-scoped users, belong to exactly one church.
--                  Carry a role (LEAD or CATECHIST) and an active flag.
--
-- Kept in separate tables (not unified under "user" with a role column)
-- per ADR-0005. Makes the privilege boundary visible in the schema.

-- =====================================================================
-- super_admin: system-level operators
-- =====================================================================

CREATE TABLE super_admin (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(200) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_super_admin_email UNIQUE (email)
);

-- =====================================================================
-- catechist: tenant-scoped users within a church
-- =====================================================================

CREATE TABLE catechist (
    id         UUID         PRIMARY KEY,
    church_id  UUID         NOT NULL,
    email      VARCHAR(200) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_catechist_church
        FOREIGN KEY (church_id) REFERENCES church(id),

    CONSTRAINT uq_catechist_church_email
        UNIQUE (church_id, email),

    CONSTRAINT ck_catechist_role
        CHECK (role IN ('LEAD', 'CATECHIST')),

    -- Composite unique constraint — required for downstream composite
    -- foreign keys (e.g., catechist_assignment). Makes (id, church_id)
    -- a valid reference target for cross-tenant integrity checks.
    CONSTRAINT uq_catechist_id_church
        UNIQUE (id, church_id)
);

CREATE INDEX idx_catechist_church ON catechist(church_id);
