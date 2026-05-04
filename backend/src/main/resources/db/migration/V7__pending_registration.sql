-- V7__pending_registration.sql
--
-- Introduces the first table that stores sensitive personal data about
-- minors. This is the consent ledger: every parent-submitted registration
-- lands here as an immutable record, regardless of whether it is later
-- approved, rejected, or redacted.
--
-- Lifecycle (per ADR-0003 and the Sprint 2 sprint goal):
--
--   1. Parent submits via POST /public/classes/{slug}/registrations
--      → row inserted with status = 'PENDING', consent metadata captured
--   2. Lead reviews via POST /pending-registrations/{id}/{approve|reject}
--      → status flips, reviewed_at + reviewed_by_catechist_id written once
--   3. (Optional, much later) LGPD right-of-erasure invoked
--      → sensitive fields nulled, sensitive_data_redacted_at set;
--        identity, status, and audit metadata preserved
--
-- The row is NEVER deleted. This table IS the audit trail.
--
-- Why a separate table from `child` (V8):
--   - Different lifecycles: pending is write-once-then-frozen;
--     child is editable.
--   - Different access surfaces: pending is created by an anonymous
--     public endpoint; child is read-write only by authenticated
--     catechists.
--   - Approval is a deliberate promotion, not a status flip on a
--     shared row. The ledger survives the promotion.
--
-- Composite FKs: both `klass_id` and `reviewed_by_catechist_id` carry
-- their `church_id` companion, making cross-tenant references
-- structurally impossible (same pattern as V4's catechist_assignment).
-- The reviewer FK is nullable-friendly: when reviewed_by_catechist_id
-- is NULL, the composite reference is treated as absent.

CREATE TABLE pending_registration (
    id                          UUID         PRIMARY KEY,
    church_id                   UUID         NOT NULL,
    klass_id                    UUID         NOT NULL,
    status                      VARCHAR(20)  NOT NULL,

    -- Submitted child identity. Tier-2 PII (ADR-0003).
    child_first_name            VARCHAR(200) NOT NULL,
    child_last_name             VARCHAR(200) NOT NULL,

    -- Submitted safety info. Tier-3 PII (ADR-0003).
    -- `allergies` may legitimately be NULL ("none provided") or a
    -- short text; "none" is also acceptable as user input.
    allergies                   TEXT,

    -- JSONB array of {name, phone} objects. Shape validated at the
    -- application layer (S02-08); the DB only enforces "is JSON."
    -- Defaulted to '[]' so the column has a consistent invariant
    -- shape — empty array means "no contacts", never NULL.
    emergency_contacts          JSONB        NOT NULL DEFAULT '[]'::jsonb,

    -- For catechist follow-up. Tier-2 PII; cleared on redaction.
    parent_contact_email        VARCHAR(200) NOT NULL,

    -- Consent capture. Write-once at submission; never modified,
    -- not even by redaction (consent metadata IS the audit's point).
    consent_version             VARCHAR(20)  NOT NULL,
    consent_granted_at          TIMESTAMPTZ  NOT NULL,

    submitted_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Review metadata. NULL until a Lead approves or rejects.
    -- Once written, never changed.
    reviewed_by_catechist_id    UUID,
    reviewed_at                 TIMESTAMPTZ,
    rejection_reason            VARCHAR(500),

    -- LGPD redaction marker. NULL means "sensitive fields above
    -- still hold their original values." Set means "sensitive
    -- fields have been cleared at this timestamp."
    sensitive_data_redacted_at  TIMESTAMPTZ,

    CONSTRAINT fk_pending_registration_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    CONSTRAINT fk_pending_registration_reviewer
        FOREIGN KEY (reviewed_by_catechist_id, church_id)
        REFERENCES catechist(id, church_id),

    CONSTRAINT ck_pending_registration_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Composite index supporting the primary read query from S02-09:
--   SELECT ... WHERE klass_id = ? AND status = 'PENDING'
-- Also serves single-column lookups by klass_id alone (leading prefix).
CREATE INDEX idx_pending_registration_klass_status
    ON pending_registration(klass_id, status);