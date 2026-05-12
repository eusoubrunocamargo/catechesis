# Sprint 3 — Backlog

- **Sprint window (planned):** 2026-05-16 → 2026-05-29
- **Sprint goal:** A catechist can configure a per-class snack menu and
  create an event; a parent can open the event link, authenticate with
  a 6-digit PIN, claim a snack slot for their child, and mark absence.
  This is the heart of the MVP — the original problem the system was
  conceived to solve.
- **Approach:** Item-by-item, didactic walkthrough. Design first, then
  code, then verification. Same rhythm as Sprint 1 and Sprint 2.
- **Sprint size:** 16 items. Larger than Sprint 2's 14, justified by
  the breadth of new domain (events, snack lists, parent-facing flow).

## Items

| ID | Title | Size |
|----|-------|------|
| S03-01 | Draft ADR-0006 — Parent Authentication via Per-Child PIN | S |
| S03-02 | Flyway V10 — `parent_pin_hash` columns on roster tables | S |
| S03-03 | Public registration: add `parentPin` field; copy hash to Child at approval | M |
| S03-04 | Flyway V11 — `klass.display_slug` column | S |
| S03-05 | Klass display slug: entity, request, validation, controller | M |
| S03-06 | Flyway V12 — `event` table with lifecycle states | M |
| S03-07 | Flyway V13 — `class_menu` and `class_menu_item` tables | M |
| S03-08 | Event + menu JPA entities and lifecycle methods | M |
| S03-09 | `POST /classes/{id}/menu` — configure per-class snack menu | M |
| S03-10 | `POST /classes/{id}/events` — create event with inherited slots | L |
| S03-11 | `POST /events/{id}/publish` and `/lock` — lifecycle transitions | S |
| S03-12 | Flyway V14 — `snack_claim` and `absence_mark` tables | M |
| S03-13 | `POST /public/events/{classSlug}/{eventSlug}/identify` — PIN lookup | M |
| S03-14 | `POST /public/events/{classSlug}/{eventSlug}/claim` — claim slot | L |
| S03-15 | `POST /public/events/{classSlug}/{eventSlug}/absence` — mark absence | M |
| S03-16 | `GET /events/{id}/dashboard` — catechist's consolidated view | M |
| S03-17 | Public-surface security tests for the event flow (quality gate) | L |
| S03-18 | Sprint close: report + project_church.md update | XS |

Sizes (rough effort, not story points): XS = ~30 min, S = 1-2 hr, M = 2-4 hr, L = 4-8 hr.

## Non-goals (explicit)

To prevent scope creep, these are deliberately out of Sprint 3:

- **Catechist-assignment-based row-level authorization.** The
  `catechist_assignment` table from Sprint 1 stays unused at the
  application layer. Every new admin endpoint is Lead-only, matching
  the Sprint 2 pattern. Adding row-level auth is a separate item for
  Sprint 4.
- **PIN reset workflow.** No `/child/new-pin` reset endpoint in Sprint 3.
  Catechists who need to reset a PIN do it directly in the database
  (or it's deferred to Sprint 4). The ADR-0006 will note this gap.
- **Auto-archive and auto-lock scheduled jobs.** Event lifecycle
  transitions are manual (`POST /events/{id}/publish`,
  `POST /events/{id}/lock`). Scheduled lock/archive jobs are
  Sprint 4 work.
- **Per-class override of slot count.** Every menu item has exactly 4
  slots in MVP. Configurable override is Sprint 4+.
- **Parent UI for sibling consolidation.** Parents with siblings in the
  same class re-enter the relevant PIN per child. The form alert
  ("register siblings one at a time") and event-page prompt
  ("re-enter for the sibling") are the entire sibling UX.
- **Catechist dashboard frontend.** The dashboard endpoint
  (S03-16) returns JSON. No HTML UI for it in Sprint 3 — frontend
  work begins in Sprint 5.
- **Real authentication.** The dev auth filter from Sprint 1 stays
  in place. Google OAuth is Sprint 5.

## Sequencing notes

A few items have non-obvious dependencies:

- **S03-01 (ADR-0006) must land first.** It's the source of truth for
  the PIN model. S03-02 and S03-03 build against it. If the ADR
  surfaces a design issue mid-drafting, S03-02 changes accordingly.
- **S03-04/05 (klass display slug) must land before S03-10**
  (event creation), because event URLs use the display slug.
- **S03-07 (menu schema) must land before S03-10** (event creation),
  because event creation inherits the menu as slots.
- **S03-12 (claim/absence schema) must land before S03-14 and S03-15**,
  because those endpoints insert into those tables.
- **S03-17 (quality gate) must land last among feature items**,
  because it exercises the full public surface assembled in
  S03-13/14/15.

## Risks

- **PIN collisions across the church.** Two unrelated parents pick the
  same 6-digit PIN. This isn't really a collision (PIN is scoped to a
  specific child, not globally), but a parent's PIN entered on the
  wrong class's event link will find no matching child and return
  401. This is the system working correctly. Worth a property in the
  quality gate to verify.
- **Two-part URL scheme is new.** The event URL is
  `/e/{classSlug}/{eventSlug}` — using the human-readable
  `klass.display_slug`, not the opaque `klass.public_slug`. This is
  a deliberate departure from Sprint 2's slug pattern (which used the
  opaque slug for enumeration resistance). The rationale is in the
  S03-04 design note.
- **Slot uniqueness at the DB level.** S03-12 must put a `UNIQUE`
  constraint on `(slot_id)` in `snack_claim`. The concurrent-claim
  test in S03-17 is the verification.
- **Event slug collisions within a class.** Two events with the same
  date will collide on slug. Decided at S03-06: `(class_id,
  event_slug)` is unique, and event_slug is derived from event date
  + a slugifying rule. If a catechist creates two events on the same
  date for the same class (legitimate use case for morning + evening
  sessions), the second one fails — that's the constraint working.
  Catechist can rename one explicitly.

## Items in detail

### S03-01 — Draft ADR-0006: Parent Authentication via Per-Child PIN

**Background.** The roadmap proposed a "device binding" mechanism for
attributing snack claims to parents without forcing login. A grill
session at Sprint 3 kickoff established that device binding was a
means to a friction-reduction end, and a simpler mechanism — a
per-child PIN — achieves the same end without device-side state. This
ADR documents the chosen mechanism, the rationale, and the model.

**Scope of the ADR.**

- Decision: PIN-based parent authentication for snack claims and
  absence marking
- Mechanism: 6-digit numeric PIN chosen by parent at registration;
  hashed at rest using BCrypt; never displayed back to anyone
- Cardinality: one PIN per child (not per parent). Siblings get
  independent PINs.
- Lifecycle: permanent; reset workflow is deferred (a future ADR or
  in-sprint task will address it when needed)
- Brute-force defense: per-IP rate limit on `/public/**` (existing
  from S02-07); no per-child lockout for MVP
- Storage: `parent_pin_hash` column on `Child` and
  `pending_registration`, copied at approval time
- Lookup: `findByClassAndPinHash(klassId, pinHash)` returning
  `Optional<Child>` — no list, no sibling resolution

**What this ADR explicitly rejects.**

- Device binding (cookie/localStorage-based identification of the
  parent's device). Rejected because the goal was friction
  reduction, not a security property, and PINs achieve the same end
  with less infrastructure.
- Parent entity / sibling unification logic. Rejected because the
  added model complexity didn't earn its keep — the parent UX cost
  of multiple PINs is small.
- Per-child PIN reset workflow in this sprint. Deferred.

**Acceptance criteria.**

- ADR file present at `docs/adr/0006-parent-authentication-via-pin.md`
- Reflects the model above
- References Sprint 2's redaction-scope ADR (the PIN is *not* part
  of the LGPD redaction set, since it's the access credential)
- Status: Accepted
- ADR table in `project_church.md` updated to include it

**Size:** S.

---

### S03-02 — Flyway V10: `parent_pin_hash` columns on roster tables

**Background.** The PIN must be stored at the row level on each Child,
and carried through the approval pipeline from PendingRegistration.

**Migration content.**

```sql
ALTER TABLE pending_registration
    ADD COLUMN parent_pin_hash VARCHAR(60) NOT NULL;
    -- BCrypt hashes are exactly 60 chars

ALTER TABLE child
    ADD COLUMN parent_pin_hash VARCHAR(60) NOT NULL;

-- An index on (klass_id, parent_pin_hash) accelerates the lookup
-- at /public/events/.../identify
CREATE INDEX idx_child_klass_pin_hash
    ON child(klass_id, parent_pin_hash);
```

**Notes.**

- The columns are `NOT NULL`. Every pending registration submitted
  after V10 must carry a PIN hash. Pre-V10 rows would violate this
  constraint; since the dev DB is fresh and no production DB exists,
  this is fine. (For future-Bruno: if a production DB ever existed
  before V10, this migration would need backfill logic. Not the case
  today.)
- `VARCHAR(60)` is the BCrypt hash size (`$2a$10$...`, exactly 60
  characters). Not configurable.
- The index covers the PIN-lookup endpoint. Worth a query-plan check
  during S03-13 to verify the index actually gets used.

**Acceptance criteria.**

- V10 migration runs cleanly on a fresh DB
- Pre-existing tests in the suite still pass (entity update happens
  in S03-03 — V10 alone with the test fixture's existing
  entity-side will break tests; that's expected and the test fix is
  S03-03's scope)

**Size:** S.

---

### S03-03 — Public registration: PIN field, hash at submission, copy at approval

**Background.** The parent's registration form gains a new field:
`parentPin`. Validated as a 6-digit numeric string. Hashed at
submission via BCrypt. Carried on the PendingRegistration row.
Copied to the Child row at approval.

**Touched files.**

- `PublicRegistrationRequest` — add `parentPin` field with
  `@NotBlank`, `@Pattern(regexp = "\\d{6}")`
- `PendingRegistration` — add `parentPinHash` field, getter, setter,
  `@Column(name = "parent_pin_hash", nullable = false, length = 60)`,
  updated constructor
- `Child` — same, plus `parent_pin_hash` set via constructor at
  approval time
- `PendingRegistrationService.submit` — hash the submitted PIN with
  BCrypt before persisting the row
- `PendingRegistrationService.approve` — copy `parent_pin_hash` from
  the PendingRegistration to the new Child
- `RosterRedactionService` — verify the redaction logic does NOT
  clear `parent_pin_hash` (it's the credential, not Tier-2/3 data).
  Existing tests in `RosterRedactionServiceIntegrationTests` need an
  assertion added: post-redaction, `parent_pin_hash` is unchanged
- Quality-gate tests (`PublicSurfaceSecurityIntegrationTests`) —
  property 7's redaction-audit-skeleton test needs an assertion that
  `parent_pin_hash` survives redaction

**Dependencies.**

- Add BCrypt to the build. Spring Boot's `spring-boot-starter-security`
  already includes `BCryptPasswordEncoder`. Use that — no new
  dependency.
- Wire `BCryptPasswordEncoder` as a `@Bean` in
  `InitialSecurityConfig` (or a new `PasswordEncodingConfig` if the
  encoder feels out of place in the dev-auth file).

**Acceptance criteria.**

- A valid public registration submission with `parentPin: "384921"`
  produces a PendingRegistration row whose `parent_pin_hash` is a
  60-char BCrypt hash that verifies against the submitted PIN
- A submission without `parentPin` returns 400
- A submission with `parentPin: "abc123"` returns 400 (Pattern
  violation)
- Approval flow copies the hash to the new Child row
- Redaction does not clear the hash
- All existing tests pass
- New tests: PIN validation; hash persistence; hash copy at approval;
  hash survives redaction

**Risks.**

- The BCrypt hash is non-deterministic (salt varies). Tests must
  assert hash *verifiability*, not hash *equality*. Use
  `BCryptPasswordEncoder.matches(rawPin, storedHash)` for assertions.
- The PIN is a Tier-3-adjacent value — leaking it would let an
  attacker impersonate a parent. Avoid logging the raw PIN anywhere;
  the existing logging review process should catch this, but worth a
  manual check.

**Size:** M.

---

### S03-04 — Flyway V11: `klass.display_slug` column

**Background.** Sprint 3 introduces event URLs of the form
`/e/{classSlug}/{eventSlug}` where `classSlug` is the *human-readable*
identifier of a class. Today, `klass.public_slug` is opaque
(10-char base62) and used for registration links. The display slug is a
separate, human-readable identifier chosen by the catechist at class
creation.

**Migration content.**

```sql
ALTER TABLE klass
    ADD COLUMN display_slug VARCHAR(60) NOT NULL DEFAULT 'placeholder',
    ADD CONSTRAINT uq_klass_church_display_slug
        UNIQUE (church_id, display_slug);

-- The default 'placeholder' is a one-shot for the migration to run
-- against existing rows. The application layer will require the
-- display_slug to be non-empty going forward.
ALTER TABLE klass
    ALTER COLUMN display_slug DROP DEFAULT;
```

**Notes.**

- Unique within a church, not globally. Two different parishes can
  both have a class called "sementinhaIIIA".
- 60 characters is generous; the catechist chooses a memorable
  identifier (e.g. "sementinhaIIIA", "perseveranca-2", "primeira-eucaristia").
- `(church_id, display_slug)` composite unique enforces tenant scope
  — Sprint 1's pattern. No global uniqueness.

**Acceptance criteria.**

- V11 runs cleanly
- Existing klass rows get the placeholder value (and the test fixture
  in S03-05 will explicitly set a real value)

**Size:** S.

---

### S03-05 — Klass display slug: entity, request, validation, controller

**Background.** With V11 in place, the application layer needs to
accept and validate the new field.

**Touched files.**

- `Klass` entity — add `displaySlug` field, getter/setter, updated
  constructor, `@Column(name = "display_slug", nullable = false,
  length = 60)`
- `CreateKlassRequest` — add `displaySlug` field; validate with
  `@NotBlank`, `@Pattern(regexp = "[a-zA-Z0-9-]+")`, `@Size(max = 60)`
- `KlassService.createKlass` — pass the display slug through;
  catch `DataIntegrityViolationException` on the unique constraint
  and translate to a 409 Conflict
- `KlassController` — request body includes the new field
- Existing tests in `KlassControllerIntegrationTests` and any test
  fixture creating Klass rows — update to provide a display slug

**Acceptance criteria.**

- Class creation with `displaySlug: "sementinhaIIIA"` succeeds; the
  row has the value
- Class creation with a duplicate display slug *within the same
  church* returns 409
- Class creation with the same display slug in *different churches*
  succeeds (cross-tenant isolation)
- All existing Klass tests pass (test fixture updates required)

**Risks.**

- The validation regex `[a-zA-Z0-9-]+` is conservative. Catechists in
  Brazilian Portuguese may want accented characters (e.g.,
  "perseverança"). Decision: ASCII-only for MVP, document this in
  S03-05's commit message, and revisit if real users push back.
  Accented characters in URLs are messy regardless (percent-encoding,
  copy-paste behavior), so this is a defensible default.

**Size:** M.

---

### S03-06 — Flyway V12: `event` table with lifecycle states

**Background.** Events represent a single class meeting on a single
date. The catechist creates an event, parents claim snack slots for
the event, and the event progresses through DRAFT → OPEN → LOCKED →
ARCHIVED states.

**Migration content.**

```sql
CREATE TABLE event (
    id UUID PRIMARY KEY,
    church_id UUID NOT NULL,
    klass_id UUID NOT NULL,
    event_slug VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    event_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,  -- DRAFT, OPEN, LOCKED, ARCHIVED
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,

    CONSTRAINT fk_event_klass FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    CONSTRAINT uq_event_klass_slug UNIQUE (klass_id, event_slug),

    CONSTRAINT chk_event_status CHECK (
        status IN ('DRAFT', 'OPEN', 'LOCKED', 'ARCHIVED')
    )
);

CREATE INDEX idx_event_klass_date ON event(klass_id, event_date);
```

**Notes.**

- Composite FK on `(klass_id, church_id)` — Sprint 1's pattern,
  enforcing tenant integrity at the DB level.
- `event_slug` is unique *within* a class. Two classes can have
  events with the same slug. `(klass_id, event_slug)` is the unique
  key the URL routing depends on.
- Status as a `VARCHAR` with CHECK constraint, not an SQL ENUM.
  Easier to evolve.
- Audit timestamps follow the Sprint 1 pattern (`created_at`,
  `updated_at`), populated by Hibernate's `AuditingEntityListener`.
- Lifecycle timestamps (`published_at`, `locked_at`, `archived_at`)
  are entity-managed, set by lifecycle methods.

**Acceptance criteria.**

- V12 runs cleanly
- Composite FK rejects an event whose `(klass_id, church_id)` doesn't
  match a real klass

**Size:** M.

---

### S03-07 — Flyway V13: `class_menu` and `class_menu_item` tables

**Background.** A class has a single menu of snack items. Each event
inherits the menu's items as slots. The menu is configured by the
catechist (Lead, in MVP) once per class and used by every subsequent
event.

**Migration content.**

```sql
CREATE TABLE class_menu (
    id UUID PRIMARY KEY,
    church_id UUID NOT NULL,
    klass_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_menu_klass FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    -- One menu per class
    CONSTRAINT uq_menu_klass UNIQUE (klass_id, church_id)
);

CREATE TABLE class_menu_item (
    id UUID PRIMARY KEY,
    church_id UUID NOT NULL,
    menu_id UUID NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_menu_item_menu FOREIGN KEY (menu_id, church_id)
        REFERENCES class_menu(id, church_id),

    CONSTRAINT uq_menu_item_name UNIQUE (menu_id, item_name)
);
```

**Notes.**

- One menu per class (UNIQUE on `(klass_id, church_id)`). Replacing
  the menu replaces all items.
- Item names are unique within a menu (no two "pão de queijo"
  entries). The catechist's UX handles duplicates explicitly.
- `sort_order` lets the catechist control display order. Default 0
  is fine for initial creation.

**Acceptance criteria.**

- V13 runs cleanly
- Cross-tenant composite FKs are correctly defined

**Size:** M.

---

### S03-08 — Event + menu JPA entities and lifecycle methods

**Background.** The entity layer for events and menus. Follows the
Sprint 2 pattern: explicit Java (no Lombok), `@Column` annotations,
lifecycle methods with invariant guards.

**New files (rough).**

- `event/Event.java` — entity with status enum, lifecycle methods
  (`publish()`, `lock()`, `archive()`, each guarding the legal
  state transitions)
- `event/EventStatus.java` — enum
- `event/EventRepository.java`
- `event/menu/ClassMenu.java` — entity
- `event/menu/ClassMenuItem.java` — entity
- `event/menu/ClassMenuRepository.java` — repository with custom
  query `findByKlassId` returning `Optional<ClassMenu>` plus the
  items eagerly loaded

**Acceptance criteria.**

- All entities have explicit getters, setters, no-arg constructors
- Lifecycle methods on Event guard transitions:
  - `publish()` requires DRAFT, transitions to OPEN, sets
    `published_at`
  - `lock()` requires OPEN, transitions to LOCKED, sets `locked_at`
  - `archive()` requires LOCKED, transitions to ARCHIVED, sets
    `archived_at`
  - Each throws `IllegalStateException` on a wrong-state transition
- Smoke test passes: round-trip an Event through DRAFT → OPEN → LOCKED
- Smoke test passes: ClassMenu with 3 items round-trips correctly

**Size:** M.

---

### S03-09 — `POST /classes/{id}/menu` — configure per-class snack menu

**Background.** The catechist defines the menu for a class. One menu
per class. POSTing again replaces the existing menu.

**Endpoint shape.**
POST /classes/{id}/menu
Authorization: Lead
Request body:
{
"items": [
{"itemName": "pão de queijo", "sortOrder": 1},
{"itemName": "suco de uva", "sortOrder": 2},
...
]
}
Response: 201 Created with the menu including item IDs
**Service behavior.**

- Validate tenant scope (`@PreAuthorize` + `TenantContext`)
- If a ClassMenu already exists for this klass, delete it (and its
  items) and create a new one — replace semantics
- Insert the new ClassMenu + items in one transaction
- Return the new menu

**Acceptance criteria.**

- Creating a menu with 3 items succeeds, returns the menu with item
  IDs
- POSTing again replaces the menu (old items gone, new items present)
- Cross-tenant request returns 404
- Empty items list returns 400 (validation)
- Duplicate item names within the same request return 400 (or 409,
  per Sprint 1's pattern for unique-constraint violations)

**Size:** M.

---

### S03-10 — `POST /classes/{id}/events` — create event with inherited slots

**Background.** The catechist creates an event for a class. The event
slug is derived from the event date + title. At creation, slots are
*not* materialized as separate rows — the menu *is* the slot source.
The slot is identified by `(event_id, menu_item_id, slot_index)`
where `slot_index` is 0–3 (default 4 slots per item).

**Endpoint shape.**
POST /classes/{id}/events
Authorization: Lead
Request body:
{
"title": "catequese 11/05/2026",
"eventDate": "2026-05-11",
"eventSlug": "catequese-11-05-2026"  // optional; auto-generated if absent
}
Response: 201 Created
{
"id": "<uuid>",
"classSlug": "sementinhaIIIA",
"eventSlug": "catequese-11-05-2026",
"eventDate": "2026-05-11",
"publicUrl": "http://localhost:5173/e/sementinhaIIIA/catequese-11-05-2026",
"status": "DRAFT"
}
**Service behavior.**

- Validate tenant scope
- Verify a ClassMenu exists for the target class; reject with 409 if
  no menu is configured ("configure menu before creating events")
- Derive `event_slug` from the date if not provided (e.g.
  `catequese-2026-05-11`)
- Persist the Event in DRAFT status
- Compose and return the public URL (using the klass's
  `displaySlug` and the event's `eventSlug`)

**Risks.**

- The `eventSlug` must be unique within the class
  (`uq_event_klass_slug`). If a catechist creates two events on the
  same date, the auto-derived slug collides. Catch the unique
  constraint violation and return 409 with a clear error message.
- The "menu must exist" check should be a service-layer guard. A
  database FK between Event and ClassMenu is *not* added — events
  exist independently of the menu, and the menu may change between
  events. The dashboard endpoint (S03-16) consults whatever menu is
  current at dashboard-read time.

**Acceptance criteria.**

- Creating an event for a class with a menu succeeds
- Creating an event for a class *without* a menu returns 409
- Two events with the same auto-generated slug return 409 on the
  second
- The publicUrl in the response uses `klass.displaySlug` (not the
  opaque `publicSlug`)
- Cross-tenant request returns 404

**Size:** L.

---

### S03-11 — `POST /events/{id}/publish` and `/lock`

**Background.** The event lifecycle has manual transitions:
DRAFT → OPEN (publish), OPEN → LOCKED (lock). The archive transition
is deferred to Sprint 4.

**Endpoint shapes.**
POST /events/{id}/publish    -> transitions DRAFT to OPEN
POST /events/{id}/lock       -> transitions OPEN to LOCKED

POST /events/{id}/publish    -> transitions DRAFT to OPEN
POST /events/{id}/lock       -> transitions OPEN to LOCKED

POST /public/events/{classSlug}/{eventSlug}/identify
Public (rate-limited)
Request body:
{ "pin": "384921" }
Response: 200 OK
{
"childId": "<uuid>",
"childFirstName": "Maria",
"childLastName": "Silva",
"eventStatus": "OPEN",
"alreadyClaimed": false,
"alreadyAbsent": false
}
Or: 401 Unauthorized (PIN not found in this class)
Or: 404 Not Found (event slug invalid)
Or: 409 Conflict (event is DRAFT, LOCKED, or ARCHIVED — claims not allowed)

**Service behavior.**

- Resolve `(classSlug, eventSlug)` to an Event via:
  `Klass.findByChurchAndDisplaySlug → Event.findByKlassAndSlug`
- 404 on either lookup failure
- 409 if event is not OPEN
- Look up Child by `(klass_id, parent_pin_hash)` after hashing the
  submitted PIN
- 401 on no match
- Return child summary + event state + the parent's existing claim
  status

**Risks.**

- BCrypt hashing is slow (~100ms per attempt by design). The
  PIN-lookup query can't simply do `WHERE pin_hash = bcrypt(input)`
  — BCrypt produces different hashes for the same input due to salt.
  The query must load *all* children in the class, then iterate and
  `matches()` each one against the submitted PIN. For ~30 children
  per class, this is ~3 seconds. **Unacceptable.**
- **Alternative:** Use a fast, deterministic hash (SHA-256 with a
  shared salt) for *PIN lookup*, with the actual BCrypt as a
  secondary verification. Two-step: fast index lookup, slow
  verify. This requires a second column (`parent_pin_lookup_hash`
  = SHA-256 of the PIN with a known salt) on Child.
- **Alternative 2:** Use a deterministic salt (e.g., the
  `child_id` as the salt input — though child_id is a UUID we don't
  know until insert). Probably not workable.
- **Final approach for MVP:** Use SHA-256 of the PIN (with a
  per-deployment secret pepper from config) for the lookup column.
  Don't use BCrypt for PINs — BCrypt is for passwords with millions
  of guesses. The PIN space (1M values) is small enough that the
  per-IP rate limit is the real defense; the hash just prevents an
  attacker with read-only DB access from learning PINs.
  **This is the right decision but warrants a paragraph in
  ADR-0006.**

**S03-13 implementation choice:** SHA-256-with-pepper for lookup
column. Column renamed from `parent_pin_hash` to `parent_pin_hash`
still — same name, just different algorithm. ADR-0006 captures the
rationale.

**Acceptance criteria.**

- Valid PIN against a child in the event's class → 200 with summary
- Wrong PIN → 401 with no information about whether the PIN exists
  elsewhere
- Unknown class slug or event slug → 404
- DRAFT/LOCKED/ARCHIVED event → 409
- Rate limit: hit the limit, get 429

**Size:** M.

---

### S03-14 — `POST /public/events/{classSlug}/{eventSlug}/claim`

**Background.** Once identified, the parent picks a slot to claim.

**Endpoint shape.**

POST /public/events/{classSlug}/{eventSlug}/claim
Public (rate-limited)
Request body:
{
"pin": "384921",
"menuItemId": "<uuid>",
"slotIndex": 0
}
Response: 201 Created
{
"claimId": "<uuid>",
"menuItemName": "pão de queijo",
"slotIndex": 0,
"childFirstName": "Maria"
}
Or: 401 Unauthorized (PIN invalid)
Or: 404 Not Found (event/class invalid; menuItemId invalid)
Or: 409 Conflict (slot already claimed; event not OPEN; absence already marked)

**Service behavior.**

- Validate via S03-13's PIN-resolution flow (effectively duplicate
  the identify logic, or factor out a `ParentAuthService.resolve`
  helper)
- Verify the menu_item_id belongs to the event's class's menu
- Insert a SnackClaim row
- The `uq_event_slot` unique constraint enforces concurrency safety:
  the second simultaneous claim attempt fails with
  `DataIntegrityViolationException`, translated to 409
- If the child has marked absence for this event, reject the claim
  with 409 ("absence marked — clear absence first")

**Acceptance criteria.**

- Valid claim → 201 with claim details
- Second claim on same slot → 409
- Wrong PIN → 401
- Concurrent claims on the same slot from two different parents:
  exactly one succeeds (verified in S03-17 quality gate)
- Marking absence then claiming → 409

**Size:** L.

---

### S03-15 — `POST /public/events/{classSlug}/{eventSlug}/absence`

**Background.** A parent marks their child absent from the event.

**Endpoint shape.**

POST /public/events/{classSlug}/{eventSlug}/absence
Public (rate-limited)
Request body:
{ "pin": "384921" }
Response: 201 Created
{
"absenceId": "<uuid>",
"childFirstName": "Maria"
}
Or: 401, 404, 409 (claim already made → can't mark absent)

**Service behavior.**

- Resolve PIN
- Reject if the child already has a SnackClaim for this event
  (409 "claim made — release claim first")
- Insert AbsenceMark row
- The `uq_absence_event_child` unique constraint makes marking
  absence idempotent at the DB level — second insert fails, which
  the service catches and returns 200 OK (or 201 the first time)

**Acceptance criteria.**

- Marking absence on a clean state → 201
- Marking absence twice → 200 (idempotent)
- Marking absence after claim → 409

**Size:** M.

---

### S03-16 — `GET /events/{id}/dashboard`

**Background.** The catechist's consolidated view. JSON only (no
HTML). Returns all claims grouped by menu item plus a headcount
summary.

**Endpoint shape.**

GET /events/{id}/dashboard
Authorization: Lead
Response: 200 OK
{
"event": {
"id": "<uuid>",
"title": "catequese 11/05/2026",
"eventDate": "2026-05-11",
"status": "OPEN"
},
"menu": [
{
"menuItemId": "<uuid>",
"itemName": "pão de queijo",
"slots": [
{"slotIndex": 0, "claimedBy": "Maria Silva"},
{"slotIndex": 1, "claimedBy": null},
{"slotIndex": 2, "claimedBy": null},
{"slotIndex": 3, "claimedBy": null}
]
},
...
],
"headcount": {
"totalChildrenInClass": 24,
"absencesMarked": 3,
"expectedAttendance": 21
}
}

**Service behavior.**

- Tenant-scoped to the event's church
- One query joins event → menu → menu_item → snack_claim → child
- One query counts children for the class
- One query counts absence_marks for the event

**Acceptance criteria.**

- A populated event returns the structure above with claims filled in
- An empty event returns all slots with `claimedBy: null`
- Cross-tenant request returns 404
- All claim/absence/child data correctly scoped to this event

**Size:** M.

---

### S03-17 — Public-surface security tests (quality gate)

**Background.** The Sprint 3 equivalent of S02-13. Consolidates
threat-model assertions for the new public event surface.

**Properties to test.**

1. PIN-lookup is rate-limited (existing S02-07 rate limit covers
   `/public/**`; this property re-verifies it for the new endpoints)
2. Wrong PIN returns 401 with no information distinguishing
   "PIN doesn't exist" from "PIN exists for another class"
3. Concurrent claim on the same slot: exactly one succeeds (the test
   submits 2 parallel claim requests, asserts 1×201 and 1×409, and
   verifies exactly one row exists)
4. Cross-tenant isolation: a Lead in Church A cannot read events for
   Church B's classes via the dashboard endpoint
5. Event slug enumeration: a wrong eventSlug returns 404 with the
   same response shape as a wrong classSlug
6. Lifecycle invariants: a DRAFT event rejects all public requests
   with 409, indistinguishable from a LOCKED event's rejection
7. Absence-after-claim and claim-after-absence both return 409 with
   clear messaging (verified via response body inspection)

**Test files (probably 2-3).**

- `EventPublicSurfaceSecurityTests` — most properties
- `ConcurrentClaimTest` — the concurrency test in its own file with
  Mockito or real parallel threads
- Possibly: a separate test for the rate-limit interaction (mirror of
  Sprint 2's split)

**Risks.**

- The concurrent-claim test is the tricky one. JUnit doesn't natively
  parallelize a single `@Test`; we need explicit threading. Options:
  - `ExecutorService` + 2 submit calls + `CountDownLatch` to release
    them simultaneously
  - `@RepeatedTest(100)` to stress-test the constraint at runtime
  - A pragmatic test: just verify the unique constraint via two
    sequential inserts; the parallel test is "real" verification but
    expensive
- I'd start with the sequential-inserts test (proves the constraint
  exists) and add the parallel-execution test as a stretch if time
  permits.

**Size:** L.

---

### S03-18 — Sprint close: report + project_church.md update

Same pattern as S02-14. Three artifacts:

- `docs/sprints/sprint-03-report.md` with the standard structure
- `docs/project_church.md` updated for end-of-Sprint-3 state
- ADR-0006 already exists from S03-01; just confirm it's still
  accurate

**Size:** XS.

---

## What's been deliberately left vague

A few things in this backlog will get sharper during their item:

- **The PIN-hashing algorithm.** S03-03 says BCrypt; S03-13's
  risks section walks back to SHA-256-with-pepper for *lookup*. ADR-0006
  will pin this down. The likely final answer: SHA-256 of
  (pepper + PIN) for the lookup column. No BCrypt for PINs.
- **The exact JSON shape of error responses.** Sprint 4's
  `@ControllerAdvice` work will standardize this. For now, accept the
  default Spring error responses and don't assert on their exact
  shape in tests.
- **The concurrent-claim test's exact mechanism.** S03-17 lists
  options; the right one becomes clear when writing the test.

These will be tightened during their respective items, not pre-decided.

## What this sprint is, in one sentence

Sprint 3 is the sprint where the system stops being a roster and
becomes a tool that solves the original problem.