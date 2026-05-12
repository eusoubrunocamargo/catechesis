# Sprint 2 — Report

- **Sprint window:** 2026-05-02 → 2026-05-15
- **Sprint goal:** A Lead can generate a public registration link for a
  class; a parent can submit a registration via that link; the catechist
  can approve or reject it, producing an active Child record with safety
  info.
- **Goal achieved?** Yes — all 14 work items completed.

## Outcomes

What the system can actually do as of sprint close:

- A class created via `POST /classes` now carries an opaque 10-char
  base62 slug as a stable public identifier, generated server-side at
  creation time
- A Lead can call `GET /classes/{id}/registration-link` and receive
  the slug plus an assembled public URL (`http://localhost:5173/r/{slug}`
  in dev, configurable per environment)
- Public, unauthenticated endpoint `POST /public/classes/{slug}/registrations`
  accepts parent submissions; validates input via Bean Validation;
  captures server-controlled consent metadata (version + timestamp);
  persists a `PendingRegistration` row in PENDING status
- Per-IP rate limiting (Bucket4j, in-memory) protects `/public/**`
  with a configurable per-hour bucket; 11th-of-10 requests get 429 with
  `Retry-After`
- A Lead can `GET /pending-registrations` to see PENDING submissions
  for their church (summary projection — Tier-3 fields deliberately
  excluded from the list response)
- A Lead can `POST /pending-registrations/{id}/approve` to atomically
  promote a registration into a Child + ChildSafetyInfo pair
- A Lead can `POST /pending-registrations/{id}/reject` to mark a
  submission rejected, with optional reason
- A `RosterRedactionService` clears Tier-2/3 fields from both the
  pending registration and the paired safety info while preserving
  audit identity and metadata (LGPD right of erasure)
- 70 integration + unit tests pass, including the Sprint 2 quality
  gate covering 7 public-surface security properties

## Work item results

| ID | Title | Final Status | Size | Notes |
|----|-------|--------------|------|-------|
| S02-01 | Flyway V6 — `klass.public_slug` | Done | S | Slug generator pulled forward (see Decisions) |
| S02-02 | Flyway V7 — `pending_registration` | Done | M | |
| S02-03 | Flyway V8 — `child` and `child_safety_info` | Done | M | |
| S02-04 | JPA entities | Done | M | Plus pulled-forward slug generation in `KlassService` |
| S02-05 | Slug generation utility + tests | Done | S | Executed before S02-01 (see Decisions) |
| S02-06 | `GET /classes/{id}/registration-link` | Done | S | GET, not POST (see Decisions) |
| S02-07 | Rate-limit filter for `/public/**` | Done | M | Bucket4j; in-memory; per-IP |
| S02-08 | `POST /public/classes/{slug}/registrations` | Done | L | First public, unauthenticated endpoint |
| S02-09 | `GET /pending-registrations` | Done | S | JPQL projection; Tier-3 fields excluded |
| S02-10 | `POST /pending-registrations/{id}/approve` | Done | M | Atomic Child + safety info promotion |
| S02-11 | `POST /pending-registrations/{id}/reject` | Done | S | |
| S02-12 | Redaction service for LGPD erasure | Done | M | Surfaced V9 schema fix (see Surprises) |
| S02-13 | Public-surface security tests | Done | L | Quality gate; 7 properties in 3 files |
| S02-14 | Sprint close docs | Done | XS | This file + `project_church.md` + ADR-0003 extension |

## Decisions made during the sprint

- **Executed S02-05 before S02-01.** The original backlog placed the V6
  migration (S02-01) before the slug generator utility (S02-05), but
  the migration needed the utility to backfill existing rows. Realized
  on first reading; reordered. The backlog numbering was preserved
  unchanged; the sequencing change lives in this report. Later in the
  item, decided to skip the Java migration approach entirely and use
  plain SQL with `NOT NULL UNIQUE` directly, since no production
  databases existed and dev databases could be wiped freely.
- **Pulled slug generation forward into S02-04.** Once `Klass.publicSlug`
  became a `NOT NULL` field on the entity, the existing
  `POST /classes` endpoint immediately needed to populate it. Doing
  so as part of S02-04 (rather than waiting for S02-06) was a small
  scope shuffle, not scope creep — the work would happen either way;
  only the order changed. S02-06's actual remaining scope became the
  *retrieval* endpoint, narrower than the original backlog implied.
- **`GET`, not `POST`, on `/classes/{id}/registration-link`.** Once
  slug generation moved to class creation, the endpoint became a pure
  read. REST-correct verb is GET; the original backlog said POST when
  the endpoint was creating something. Backlog text not edited, just
  noted here.
- **`AppProperties` nested-record structure** introduced in S02-06 and
  extended in S02-07 (`RateLimit`) and S02-08 (`Consent`). Each
  category of config sits under its own nested record. Cleaner than
  flat fields once three categories of config exist.
- **`EmergencyContact` as a record, separate from `EmergencyContactRequest`.**
  The domain value type lives in `roster/registration/`, the DTO with
  validation annotations lives in `dto/`. Different responsibilities;
  same shape.
- **Single-column FK from `Child` back to `PendingRegistration`**, not
  composite. The composite-FK pattern protects construction; the
  back-reference is a write-once audit pointer, not a structural
  boundary. Documented in V8 commentary.
- **Lifecycle methods on `PendingRegistration` instead of plain
  setters** (`approve`, `reject`, `redactSensitiveData`). The audit
  ledger has invariants — each transition mutates multiple fields
  together — that the entity defends, preventing the service layer
  from accidentally writing half-completed transitions.
- **Approval response shape: full `Child` DTO, not minimal `{childId}`.**
  Per backlog; frontend ergonomics. `ChildResponse` placed at
  `roster/child/dto/`, establishing the convention that each entity
  package gets its own `dto/` subdirectory.
- **Bare 201 (no body) for public registration response.** Defensive
  shape — no reflection surface, no id leakage. Asymmetric with
  approve's "return the new Child" — the asymmetry is honest because
  approval creates a tangible resource while public registration
  produces only a pending row the parent doesn't need to know about.
- **Forgiving redaction stance for missing pending registration.** If
  `child.pendingRegistrationId` references a row that no longer
  exists, the redaction proceeds on the safety info alone with a
  WARN-level log line. The opposite stance (fail loudly) would have
  meant partial completions were impossible — but at the cost of
  preventing redaction in genuinely-degraded data states.
- **Minimum identifying data preserved for audit** (LGPD redaction
  scope). The redaction clears allergies, contacts, parent email,
  notes — but preserves the child's name on both the `Child` and the
  pending registration. Rationale: a parish has legitimate retention
  obligations for child-safety records, and identity is needed to
  identify what the audit is about. Documented as an extension to
  ADR-0003 during sprint close.
- **V9 schema fix mid-sprint:** dropped `NOT NULL` from
  `pending_registration.parent_contact_email`. The column was required
  at submission (correct) but had to be nullable for redaction
  (also correct). See Surprises.

## What went well

- The JSONB round-trip via Hibernate 7's `@JdbcTypeCode(SqlTypes.JSON)`
  worked first try, despite Sprint 1's noted Jackson 2/3 split. The
  S02-04 smoke test caught what would otherwise have been a
  hard-to-diagnose runtime failure later in the sprint
- The composite FK pattern from Sprint 1 paid off twice in this
  sprint — once as the structural enforcement for V7 and V8, and once
  as the test-data-rejection mechanism that caught two test fixtures
  passing invalid data (V6 fallout, redaction test reviewer FK)
- The Sprint 2 quality gate (S02-13) consolidated seven security
  properties into 3 test files that read as documentation of the
  public surface's threat model
- Pulling slug generation forward (S02-04 rather than S02-06)
  delivered a coherent feature unit — by S02-04 close, the system
  could create classes with slugs, generate registration links, and
  the database was in a state where no klass row could lack a slug

## What didn't

- The slug-generation/migration ordering issue should have been caught
  in backlog drafting. Reordering at sprint start was cheap but
  signals that the original sprint plan didn't account for inter-item
  dependencies as carefully as it could have
- The V9 schema/entity contradiction (parent_contact_email's
  nullability) was sitting in the design from S02-02 and only
  surfaced when S02-12's redaction service first exercised the
  redaction path against a real database. A more deliberate "trace
  every redactable field through every relevant constraint" check at
  S02-02 / S02-04 would have caught it earlier
- The first cut of the atomicity test (S02-13) was *almost* a false
  green — the class-level `@Transactional` was masking the rollback
  semantics. Caught it by reading the failure carefully, but the
  pattern is worth flagging (see lessons)

## Surprises

- **JPA `nullable = false` is a schema generation hint, not a runtime
  enforcement.** When V9 dropped the `NOT NULL` constraint, the entity's
  `nullable = false` annotation didn't cause Hibernate's `validate`
  mode to complain. The annotation was honest before V9 and dishonest
  after; the application would have continued to work either way.
  Updated the annotation for honesty.
- **Spring Security's `HttpServletResponse.SC_TOO_MANY_REQUESTS`
  doesn't exist.** The constant is from Jakarta Servlet's interface,
  which still uses the original HTTP/1.1 status set from RFC 2616.
  Status 429 (RFC 6585) never made it into the constant list. Worked
  around with a `private static final int SC_TOO_MANY_REQUESTS = 429`
  in `RateLimitFilter`.
- **MockMvc does not translate unhandled exceptions into HTTP
  responses without `@ControllerAdvice`.** S02-13's atomicity test
  initially asserted `status().is5xxServerError()`, expecting Spring
  to translate the propagated `DataIntegrityViolationException` into
  a 500. Spring's default handler did not; the exception escaped
  `mockMvc.perform()` directly. Resolved by asserting on the
  exception itself via `assertThatThrownBy`. This will become a moot
  point in Sprint 4 once `@ControllerAdvice` lands.
- **Test-class `@Transactional` masks transaction-rollback assertions.**
  The atomicity test assertions were silently passing/failing based
  on the outer test transaction holding the writes in memory, not on
  the service's `@Transactional` rolling them back at the JDBC level.
  Removing class-level `@Transactional` from
  `ApprovalAtomicityIntegrationTests` made the rollback genuinely
  observable. Cleanup now happens via `@AfterEach` instead.
- **The id-supply pattern is inconsistent across entities.**
  `Klass`, `PendingRegistration`, `Child`, `ChildSafetyInfo` take `id`
  as a constructor parameter; `Church` and `Catechist` self-generate
  via inline `= UUID.randomUUID()`. Sprint 1's pattern wasn't
  uniformly applied. Not refactoring during Sprint 2; flagged as a
  candidate small refactor for Sprint 3 or later.

## Carry-over into Sprint 3

None — Sprint 2 finished its backlog cleanly.

## New follow-up items uncovered

- **`SecurityContext` lacks `requireCatechistId()` / `requireSuperAdminId()`**
  to mirror `TenantContext.requireChurchId()`. Currently every caller
  unwraps `Optional<UUID>` via `.orElseThrow(...)`. Small refactor
  worth doing at sprint close or Sprint 3 start.
- **`AppProperties` test-fixture builder.** With 3 nested categories
  (`RateLimit`, `Consent`, `publicBaseUrl`), every test that
  constructs `AppProperties` by hand has 3 arguments to maintain. Two
  test sites today; a builder becomes worthwhile when there's a
  fourth.
- **Lifecycle methods need integration coverage, not just unit
  coverage.** The V9 fallout (schema/entity contradiction) would have
  been caught earlier if S02-04 had included a redaction-roundtrip
  test against a real database. Worth applying the principle to any
  lifecycle method introduced in future sprints.
- **`@Transactional` interactions in integration tests** are subtle
  enough to warrant a documented pattern. Atomicity tests must opt
  out of class-level `@Transactional` and clean up manually. Other
  tests can stay on the default pattern. Worth a short note in a
  future testing-conventions document.
- **`SC_TOO_MANY_REQUESTS` constant** lives privately inside
  `RateLimitFilter`. If any other code needs status 429 (it shouldn't,
  but operations like client error responses might), a shared HTTP
  constants file would be the place. Not urgent.

## Metrics

- Items completed / planned: 14 / 14
- Migrations added: 4 (V6, V7, V8, V9)
- ADRs drafted or updated: 1 update (ADR-0003 extension)
- Tests added: 47 (from 23 at Sprint 1 close to 70 at Sprint 2 close)
- Source files added (production): roughly 17 new files across
  `common/`, `klass/`, `roster/registration/`, `roster/child/`,
  `roster/`
- Source files added (tests): roughly 9 new test classes
- New external dependencies: 1 (`bucket4j_jdk17-core` for the rate
  limiter)
- Schema fix discovered mid-sprint and applied: 1 (V9 dropping NOT
  NULL on `pending_registration.parent_contact_email`)