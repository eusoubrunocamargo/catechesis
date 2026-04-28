# Sprint 2 — Backlog

- **Sprint window:** 2026-05-02 → 2026-05-15
- **Sprint goal (one sentence):** A Lead can generate a public
  registration link for a class; a parent can submit a registration
  via that link; the catechist can approve or reject it, producing an
  active Child record with safety info.
- **Capacity notes:** Solo developer, evenings and weekends. Sprint 1
  used full capacity on 13 items; Sprint 2 should target similar
  velocity (~1 L, 3–4 M, 4–5 S, 1–2 XS).

## Sprint goal — detail

By the end of this sprint, the system supports the following flow,
end-to-end, via API (no frontend yet):

1. A Lead authenticates via dev mode, calls `POST /classes/{id}/registration-link`,
   and receives a public URL containing an opaque slug
2. A parent (anonymous, no auth) opens that URL and submits a
   registration form: child first/last name, allergies, emergency
   contacts, consent acceptance, parent contact email
3. The system creates a `PendingRegistration` row with status `PENDING`
   and a captured consent timestamp
4. The Lead authenticates, sees pending registrations for their class,
   and approves or rejects each one
5. On approval, an active `Child` row + `ChildSafetyInfo` row are
   created. The PendingRegistration row stays as the audit ledger.
6. The redaction flow (LGPD right of erasure) can clear sensitive
   data from both Child/ChildSafetyInfo and PendingRegistration while
   preserving the audit skeleton

The first **public, unauthenticated endpoint** in the system arrives
this sprint. Treat that surface with extra care.

## Work items

| ID | Title | Size | Status | Notes |
|----|-------|------|--------|-------|
| S02-01 | Flyway V6 — `klass.public_slug` column + backfill | S | Planned | Stable opaque slug, ~10–12 chars base62 |
| S02-02 | Flyway V7 — `pending_registration` table | M | Planned | Sensitive data, never deleted |
| S02-03 | Flyway V8 — `child` and `child_safety_info` tables | M | Planned | 1:1 child↔safety; tier-3 split per ADR-0003 |
| S02-04 | JPA entities: PendingRegistration, Child, ChildSafetyInfo | M | Planned | New `RegistrationStatus` enum |
| S02-05 | Slug generation utility + tests | S | Planned | Random base62, collision-safe |
| S02-06 | `POST /classes/{id}/registration-link` (Lead-only) | S | Planned | Idempotent: returns existing slug if class already has one |
| S02-07 | Rate-limit filter for public endpoints | M | Planned | Per-IP, in-memory bucket; first defense layer |
| S02-08 | `POST /public/classes/{slug}/registrations` (anonymous) | L | Planned | The big one — input validation, consent capture, error shape |
| S02-09 | `GET /pending-registrations` (Lead-only, current class scope) | S | Planned | List pending for review |
| S02-10 | `POST /pending-registrations/{id}/approve` (Lead-only) | M | Planned | Promotes to Child + ChildSafetyInfo atomically |
| S02-11 | `POST /pending-registrations/{id}/reject` (Lead-only) | S | Planned | Status flip + optional reason |
| S02-12 | Redaction service for LGPD erasure | M | Planned | Service layer only, no UI yet |
| S02-13 | Public-surface security tests | L | Planned | The sprint's quality gate |
| S02-14 | Update `project_church.md` and draft Sprint 2 report | XS | Planned | End of sprint |

## Item detail

### S02-01 — Flyway V6: `klass.public_slug` + backfill  _(S)_

Adds a stable opaque slug to the `klass` table so a class can have a
public registration URL. The slug:

- Is generated when the class is created (or backfilled for existing rows)
- Is `NOT NULL UNIQUE`
- Is 10–12 characters of base62 (`[A-Za-z0-9]`) — short enough to share,
  hard enough to guess
- Is **independent of the UUID PK** — the UUID stays internal; the slug
  is the public identifier

Backfill any existing classes with a freshly-generated slug. The
migration calls a generator function or uses a hand-coded pattern; for
MVP, a simple application-restart-time approach is fine if Flyway
doesn't make in-SQL slug generation easy. Alternatively, generate them
via a one-off Java migration class (`Flyway` supports `Java` migrations
that run application code — `R__` repeatable or `V6_1__SeedKlassSlugs.java`).

**Done when:**
- New `klass.public_slug` column exists, NOT NULL UNIQUE
- All existing rows have a slug
- Migration is idempotent

**Risk:** generating slugs in pure SQL during the migration is awkward.
If problematic, do it via a Java callback migration — adds complexity
but is the cleaner approach.

---

### S02-02 — Flyway V7: `pending_registration`  _(M)_

The first table that stores sensitive personal data about minors. This
is the **consent ledger** per ADR-0005.

```sql
CREATE TABLE pending_registration (
    id                  UUID         PRIMARY KEY,
    church_id           UUID         NOT NULL,
    klass_id            UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,  -- PENDING, APPROVED, REJECTED
    child_first_name    VARCHAR(200) NOT NULL,
    child_last_name     VARCHAR(200) NOT NULL,
    allergies           TEXT,                   -- nullable; "none" is acceptable
    emergency_contacts  JSONB        NOT NULL,  -- [{name, phone}, ...]
    parent_contact_email VARCHAR(200) NOT NULL, -- for catechist follow-up
    consent_version     VARCHAR(20)  NOT NULL,
    consent_granted_at  TIMESTAMPTZ  NOT NULL,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_by_catechist_id UUID,             -- nullable until reviewed
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    sensitive_data_redacted_at TIMESTAMPTZ,    -- LGPD erasure marker

    CONSTRAINT fk_pending_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    CONSTRAINT ck_pending_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_pending_klass ON pending_registration(klass_id);
CREATE INDEX idx_pending_status ON pending_registration(status);
```

**Done when:**
- Table created with all constraints
- Composite FK to klass works (try a cross-tenant insert manually; should fail)

**Open question:** is `JSONB` for `emergency_contacts` the right call?
Decided yes per the domain modeling discussion (no per-contact querying
needed). Keeps the schema small.

---

### S02-03 — Flyway V8: `child` and `child_safety_info`  _(M)_

Two tables, 1:1 relationship. The split is the materialization of the
ADR-0003 sensitivity-tier model: `child` is Tier 2, `child_safety_info`
is Tier 3 and accessed only through a narrow audited service path.

```sql
CREATE TABLE child (
    id          UUID         PRIMARY KEY,
    church_id   UUID         NOT NULL,
    klass_id    UUID         NOT NULL,
    first_name  VARCHAR(200) NOT NULL,
    last_name   VARCHAR(200) NOT NULL,
    status      VARCHAR(20)  NOT NULL,  -- ACTIVE, INACTIVE, GRADUATED
    pending_registration_id UUID,        -- nullable; back-reference for audit
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_child_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),
    CONSTRAINT fk_child_pending
        FOREIGN KEY (pending_registration_id)
        REFERENCES pending_registration(id),
    CONSTRAINT ck_child_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'GRADUATED')),
    CONSTRAINT uq_child_id_church
        UNIQUE (id, church_id)
);

CREATE INDEX idx_child_klass ON child(klass_id);

CREATE TABLE child_safety_info (
    child_id            UUID         PRIMARY KEY,
    church_id           UUID         NOT NULL,
    allergies           TEXT,
    emergency_contacts  JSONB        NOT NULL,
    notes               TEXT,
    sensitive_data_redacted_at TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_safety_child
        FOREIGN KEY (child_id, church_id)
        REFERENCES child(id, church_id)
);
```

**Done when:**
- Both tables migrated
- 1:1 relationship enforced via shared PK on `child_safety_info`
- Composite FKs verified

---

### S02-04 — JPA entities  _(M)_

Three entities matching the new tables. Patterns from Sprint 1:

- Explicit getters/setters/constructors (no Lombok)
- `Instant` for timestamps
- `@Enumerated(EnumType.STRING)` for the new `RegistrationStatus` and `ChildStatus` enums
- `TenantScoped` marker on all three (they all carry `churchId`)
- Raw FK UUIDs over JPA associations

`PendingRegistration` will need a `JsonNode` or `Map<String,Object>` field
for `emergency_contacts`. Hibernate 7 handles JSONB natively via
`@JdbcTypeCode(SqlTypes.JSON)` — worth a quick check at startup that
the round-trip works.

**Done when:**
- All three entities compile
- App boots cleanly (Hibernate `validate` mode happy)
- Three new repositories created (`extends JpaRepository`)
- Smoke test: persist + reload one of each

---

### S02-05 — Slug generation utility  _(S)_

A small, well-tested utility. ~30 lines of code, ~30 lines of test.

- `SlugGenerator.generate()` returns 10-char base62 string
- Uses `SecureRandom`, not `Random`
- Collision check: regenerate on conflict (extremely rare at this length)
- Unit test covers length, charset, distinct-output sample

Why a separate item: the slug semantics are load-bearing (they're how
parents access their class), and getting the implementation right is
worth a focused effort rather than burying it in S02-06.

---

### S02-06 — `POST /classes/{id}/registration-link`  _(S)_

Lead-only endpoint. Generates and persists a slug if the class doesn't
have one yet, returns the public URL.

**Idempotent:** if called twice, returns the same slug. Simplifies
client logic and avoids accidental URL churn.

**Response shape:**
```json
{
  "slug": "Bk7mP2x9qL",
  "publicUrl": "https://app.example.com/r/Bk7mP2x9qL"
}
```

The frontend base URL comes from configuration (`app.public-base-url`),
defaulted to `http://localhost:5173` in dev.

**Done when:**
- Endpoint implemented, OpenAPI updated
- Tests: happy path (new slug), idempotent (existing slug), wrong role
  (CATECHIST → 403), wrong tenant (Lead from another church → 404)

---

### S02-07 — Rate-limit filter for public endpoints  _(M)_

The first defensive layer for the public surface. An in-memory rate
limiter keyed by client IP, applied only to `/public/**` routes.

**Sizing rationale:** the basic shape is straightforward, but this is
the first time we add a Spring filter that's NOT for authentication, so
configuring it correctly (registration order, scope, exemptions) takes
real care.

**Implementation:**
- Bucket-based limiter (Bucket4j or hand-rolled `ConcurrentHashMap` of
  counters with periodic eviction) — Bucket4j is well-trodden, ~3 lines
  of config
- Limit: e.g., 10 registration POSTs per IP per hour
- Exceeded → 429 Too Many Requests with Retry-After header
- IP from `X-Forwarded-For` if present (for future reverse-proxy
  deployment), else `request.getRemoteAddr()`

**Done when:**
- Filter registered with order BEFORE security
- Limit configurable via `app.rate-limit.public-registration-per-hour`
- Test: 11th request from same IP within hour returns 429

**Risk:** in-memory state means rate limits don't survive restarts.
Acceptable for MVP. Sprint 4 hardening can move to Redis if needed.

---

### S02-08 — `POST /public/classes/{slug}/registrations`  _(L)_

The big one. The first public, unauthenticated endpoint. Must be
robust against hostile input.

**Behaviors:**
- No authentication required
- Rate-limited (S02-07)
- Validates input via `@Valid`: required fields, length bounds,
  email format
- Verifies the slug resolves to an active class — if not, returns 404
  WITHOUT distinguishing "doesn't exist" from "inactive" (avoid
  information leakage)
- Captures consent: stores `consent_version` from a config property +
  `consent_granted_at = now()`
- Creates the `PendingRegistration` row with status `PENDING`
- Returns 201 with a minimal body (just an acknowledgement; never echo
  back the parent's input — reduces injection-attack surface)

**Tenant resolution:** comes from the slug, not from auth. The slug
identifies a class, the class has `church_id`, the `PendingRegistration`
inherits both.

**Anti-abuse considerations:**
- No "registration exists" indication on duplicate submissions —
  silently accept (idempotency at the human level)
- No echo of submitted data in error responses
- Field-level validation errors return field names but not values

**Done when:**
- Endpoint works end-to-end
- All anti-abuse properties verified by tests
- Rate limiter actually fires when exceeded

---

### S02-09 — `GET /pending-registrations`  _(S)_

Lead-only. Returns pending registrations for the Lead's classes.

**Filter logic:**
- `status = 'PENDING'` (default; query param to override for review history)
- Joined to `klass` to filter by Lead's church
- Ordered by `submitted_at DESC`

**Response:** list of pending registration DTOs. Includes child name,
parent contact email, submission time. Does NOT include allergies or
emergency contacts in the list view — those appear only in the detail
view (S02-10 reads them when approving).

**Done when:**
- List endpoint returns only the Lead's pending registrations
- Cross-tenant test: Lead-A doesn't see Lead-B's pending registrations

---

### S02-10 — `POST /pending-registrations/{id}/approve`  _(M)_

Lead-only. Promotes a pending registration into an active Child +
ChildSafetyInfo atomically.

**Algorithm:**
1. Verify the pending registration exists and is in `PENDING` status
2. Verify the requester is a Lead in the registration's church
3. Insert `Child` and `ChildSafetyInfo` rows in one transaction
4. Update PendingRegistration: status → `APPROVED`, set
   `reviewed_by_catechist_id`, set `reviewed_at`
5. Return the new Child as a DTO (without safety info — the response
   is for confirmation, not data display)

**Edge cases:**
- Already approved → 409 Conflict
- Wrong tenant → 404
- Wrong role → 403

**Done when:**
- Atomic promotion works (all-or-nothing via `@Transactional`)
- Tests cover happy path + the three edge cases

---

### S02-11 — `POST /pending-registrations/{id}/reject`  _(S)_

Lead-only. Marks a pending registration as `REJECTED`. Optional
rejection reason in body.

Simpler than approve — no Child creation, just a status flip.

**Done when:**
- Endpoint implemented
- Tests: happy path, double-reject (409), wrong role (403)

---

### S02-12 — Redaction service for LGPD erasure  _(M)_

Implements the right-of-erasure mechanism from ADR-0003. Service-layer
only — no UI in MVP. Can be invoked via integration test or a one-off
admin script.

**Behavior:**
- Takes a `Child` ID
- Sets `allergies = NULL`, `emergency_contacts = '[]'`, `notes = NULL`
  on `child_safety_info`
- Sets `sensitive_data_redacted_at = NOW()`
- Finds the originating `PendingRegistration` (via `child.pending_registration_id`)
  and applies the same redaction there: clears allergies,
  emergency_contacts, parent_contact_email; sets
  `sensitive_data_redacted_at`
- Preserves the audit skeleton (IDs, timestamps, who reviewed, status)

**Done when:**
- Service method implemented
- Test: redacted Child's safety info is empty; PendingRegistration's
  sensitive fields are empty; status and IDs survive

**Why no UI:** premature for MVP. The redaction flow is a "rare,
auditable, deliberate" action — for now, executed by the operator (you)
via a script or test. UI comes in Sprint 4 hardening.

---

### S02-13 — Public-surface security tests  _(L)_

The sprint's quality gate, equivalent to S01-12 but for the public
surface. Tests cover:

1. **Slug enumeration is hard.** Random slugs return 404 indistinguishable
   from valid slugs of inactive classes (no timing differences large
   enough to enumerate).
2. **Rate limit holds.** 11th submission from one IP within hour → 429.
3. **Cross-tenant isolation on pending registrations.** Lead-A cannot
   approve Lead-B's pending registration.
4. **Information non-leakage on errors.** Validation errors return
   field names but not echoed user input.
5. **Consent is captured.** A successful registration writes
   `consent_granted_at` matching server time and `consent_version`
   matching the configured value.
6. **Approval is atomic.** If the Child insert fails (simulated via a
   test-only constraint violation), nothing is persisted.
7. **Redaction preserves the audit skeleton.** After redaction, IDs,
   timestamps, and review metadata remain; sensitive fields are gone.

**Done when:**
- All seven assertions pass
- Tests run as part of `./mvnw test` and complete in <60 seconds

**Risk:** writing the rate-limit test cleanly requires either time
manipulation (Mockito's `Clock` injection) or making the limiter's
window configurable down to seconds for testing. Plan for ~half a day
on this alone.

---

### S02-14 — Close-of-sprint docs  _(XS)_

Same shape as S01-13. Update `project_church.md`, draft
`docs/sprints/sprint-02-report.md`.

## Risks & unknowns

- **Slug generation in Flyway migration vs. application code.** If
  generating slugs in pure SQL is awkward (and it is, in standard
  Postgres without extensions), the cleanest approach is a Java-callback
  migration. That introduces a new pattern in the project; budget extra
  time the first time it's used.
- **Rate-limit filter ordering.** Spring Security's filter chain
  ordering is subtle. Getting the rate-limit filter to run BEFORE
  Spring Security (so even unauthenticated requests get limited) but
  cleanly is non-trivial. Plan for some experimentation.
- **JSONB serialization with Hibernate 7.** The JSONB ↔ Java mapping
  is well-trodden but has version-specific quirks. If Hibernate 7's
  default handling doesn't work, falling back to a `@Convert` JSON
  string is a known-good alternative.
- **Information-leakage testing is hard.** Asserting "no timing
  difference between valid-slug-inactive-class and invalid-slug" is
  more art than science. We'll write a basic test and accept it's a
  weak assertion — real protection comes from constant-time slug
  lookup, which is a Sprint 4 concern.

## Explicitly NOT in this sprint

- **Frontend forms.** Backend + OpenAPI only, same as Sprint 1.
- **Email or WhatsApp notification of approval/rejection.** Parents
  check the registration page on their device cookie to see status.
- **Parent-facing "see my child" pages.** Out of scope.
- **Child editing endpoints.** Once approved, Child records are
  edited only by catechists, and that endpoint is Sprint 3+.
- **Photo upload.** ADR-0003 deferred this indefinitely.
- **Multi-child-per-submission.** One pending registration = one child.
  Parents with two children submit twice.
- **"Forgot which class my child is in" flow.** Slug is the auth;
  parents are responsible for keeping their link.

## Carry-over from Sprint 1

None — Sprint 1 closed cleanly.

## Open questions to resolve during the sprint

- **Should the rate limit's per-hour window be configurable per-class?**
  Probably not — global default is simpler. Defer unless a real parish
  needs it.
- **Is `parent_contact_email` required or optional?** Required for MVP
  (it's how catechists reach out for clarification). Reconsider if
  catechists report parents skipping the field.
- **Should approval send an email to the parent?** Defer — would
  require email infrastructure we don't have. Parent checks status
  on the registration page.
- **What does the public registration page URL look like in dev?**
  Likely `http://localhost:5173/r/{slug}` once the frontend exists.
  For Sprint 2, a placeholder URL in the response is fine; the
  frontend lives in Sprint 3.