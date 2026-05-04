-- V8__child_and_child_safety_info.sql
--
-- Introduces the active roster: the two tables that represent a child
-- admitted to a catechesis class after a Lead has approved their
-- pending registration.
--
--   - child:              identity row (Tier-2 PII per ADR-0003).
--                         Editable. Joined to klass within the same church.
--
--   - child_safety_info:  sensitive ancillary info (Tier-3 PII).
--                         Strict 1:1 with child via shared primary key.
--                         Accessed only through a narrow audited path
--                         (see S02-10 approval flow, S02-12 redaction).
--
-- The split is the materialization of ADR-0003's tier model. The 1:1
-- relationship is enforced *structurally* by making child_safety_info's
-- primary key the same value as the corresponding child's id. This means:
--   - A child_safety_info row cannot exist without a parent child row.
--   - At most one child_safety_info row per child (PK uniqueness).
--   - There is no second identifier for safety info; you reach it
--     deliberately, by child id, through its dedicated repository.
--
-- The child row carries pending_registration_id as a single-column FK
-- back-reference. This is a write-once audit pointer ("which submission
-- produced this child?"); not a structural tenant boundary. The composite
-- FK to klass is the tenant integrity boundary, same pattern as V4/V7.

-- =====================================================================
-- child: identity within a class
-- =====================================================================

CREATE TABLE child (
    id                       UUID         PRIMARY KEY,
    church_id                UUID         NOT NULL,
    klass_id                 UUID         NOT NULL,

    first_name               VARCHAR(200) NOT NULL,
    last_name                VARCHAR(200) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,

    -- Audit back-reference to the originating registration. NULL is
    -- allowed for future seed/import paths that don't go through the
    -- public registration flow; the approval endpoint (S02-10) will
    -- always populate this.
    pending_registration_id  UUID,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_child_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    CONSTRAINT fk_child_pending_registration
        FOREIGN KEY (pending_registration_id)
        REFERENCES pending_registration(id),

    CONSTRAINT ck_child_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'GRADUATED')),

    -- Required so child_safety_info (and any future tenant-scoped
    -- table referencing child) can use the composite-FK pattern.
    CONSTRAINT uq_child_id_church
        UNIQUE (id, church_id)
);

-- Supports the most common roster query:
--   SELECT ... FROM child WHERE klass_id = ? AND status = 'ACTIVE'
-- Also serves single-column lookups by klass_id (leading prefix).
CREATE INDEX idx_child_klass_status ON child(klass_id, status);

-- =====================================================================
-- child_safety_info: Tier-3 sensitive info, 1:1 with child
-- =====================================================================
--
-- Note: child_id is BOTH primary key AND foreign key. This is the
-- structural enforcement of the 1:1 relationship — no surrogate id,
-- no separate UNIQUE constraint needed.
--
-- The composite FK (child_id, church_id) -> child(id, church_id) keeps
-- the tenant integrity guarantee even though child_id alone would be
-- globally unique; the duplicated church_id is the deliberate cost
-- of the architecture pattern.

CREATE TABLE child_safety_info (
    child_id                    UUID         PRIMARY KEY,
    church_id                   UUID         NOT NULL,

    allergies                   TEXT,
    emergency_contacts          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    notes                       TEXT,

    -- LGPD redaction marker. NULL means "the fields above hold their
    -- original values." Set means "fields have been cleared at this
    -- timestamp." Same semantics as pending_registration's marker.
    sensitive_data_redacted_at  TIMESTAMPTZ,

    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_child_safety_info_child
        FOREIGN KEY (child_id, church_id)
        REFERENCES child(id, church_id)
);