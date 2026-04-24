-- V4__klass_and_catechist_assignment.sql
--
-- Introduces:
--
--   - klass:                the catechesis class (avoiding 'class' the
--                           reserved word). Tenant-scoped. Intentionally
--                           minimal — parishes don't have a uniform
--                           naming taxonomy, and meeting details
--                           (weekday, time, reception) vary enough that
--                           they're set at the event level, not here.
--
--   - catechist_assignment: M:N bridge between catechists and classes.
--                           Uses composite FKs to enforce that both
--                           sides belong to the SAME church.
--
-- The composite FK pattern (x_id, church_id) REFERENCES x(id, church_id)
-- is what makes cross-tenant data corruption structurally impossible.
-- Relies on the (id, church_id) unique constraints declared on both
-- catechist (V3) and klass (this migration).

-- =====================================================================
-- klass: a catechesis class within a church
-- =====================================================================

CREATE TABLE klass (
    id         UUID         PRIMARY KEY,
    church_id  UUID         NOT NULL,
    name       VARCHAR(200) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_klass_church
        FOREIGN KEY (church_id) REFERENCES church(id),

    -- Required for downstream composite foreign keys
    -- (catechist_assignment). Same pattern as catechist.
    CONSTRAINT uq_klass_id_church
        UNIQUE (id, church_id)
);

CREATE INDEX idx_klass_church ON klass(church_id);

-- =====================================================================
-- catechist_assignment: which catechists teach which classes
-- =====================================================================

CREATE TABLE catechist_assignment (
    id           UUID         PRIMARY KEY,
    church_id    UUID         NOT NULL,
    catechist_id UUID         NOT NULL,
    klass_id     UUID         NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Composite FKs: catechist, klass, and the assignment itself must
    -- all agree on church_id. Structurally enforces tenant integrity.
    CONSTRAINT fk_assignment_catechist
        FOREIGN KEY (catechist_id, church_id)
        REFERENCES catechist(id, church_id),

    CONSTRAINT fk_assignment_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    -- A catechist is assigned to a given class at most once.
    CONSTRAINT uq_assignment_catechist_klass
        UNIQUE (catechist_id, klass_id)
);

CREATE INDEX idx_assignment_klass ON catechist_assignment(klass_id);
CREATE INDEX idx_assignment_catechist ON catechist_assignment(catechist_id);

