# project_church

> **Purpose of this document.** This is the single source of truth for
> the state of the project. It is designed to be the **first file read**
> when starting a new chat session, a new development day, or onboarding
> any collaborator (human or AI). It is updated at the end of every
> sprint and kept concise — long-form detail belongs in ADRs and sprint
> reports, which this document links to.

- **Last updated:** 2026-05-15 (end of Sprint 2)
- **Active sprint:** Sprint 3 (not started)

---

## 1. Project purpose

A lightweight web system that **complements WhatsApp** for Catholic
catechesis classes. It eliminates the copy-paste race condition used
today to organize weekly snack contributions, and centralizes the
per-child registry (names, allergies, emergency contacts) that catechists
currently keep in their heads or in ad-hoc paper lists.

Primary user pain solved: the "copy-paste race" where parents edit and
re-send the same snack list in WhatsApp, silently overwriting each
other's contributions.

**Not** a replacement for WhatsApp. WhatsApp remains the communication
channel; this system is the logistics backbone for recurring events.

## 2. Constraints

- **Solo developer**, evenings and weekends
- **Corporate network** with proxy restrictions during development
- **Volunteer users** — any friction loses adoption instantly
- **Mobile-first** — ~100% of parent access is via phone
- **pt-BR only** for MVP
- **LGPD compliance** required — minors' data is involved

## 3. Architecture — top-level

- **Monorepo**, modular monolith — ADR-0001
- **Multi-tenant** via shared DB, `church_id` row-level isolation — ADR-0002
- **Security posture**: three data sensitivity tiers, privacy-by-default
  — ADR-0003
- **Authentication**: pluggable seam, dev-stub during development,
  Google OAuth in production — ADR-0004
- **Super-Admin** as a dedicated system-level role — ADR-0005
- **Stack:** Java 21 + Spring Boot 4 (backend), TypeScript + React
  framework TBD (frontend), PostgreSQL (persistence), Flyway (migrations)
- **Deployment:** single-container app + managed Postgres (target
  decided in Sprint 4)

## 4. Architectural decisions (ADRs)

| ID | Title | Status |
|----|-------|--------|
| [ADR-0001](adr/0001-monorepo-and-modular-monolith.md) | Monorepo and Modular Monolith | Accepted |
| [ADR-0002](adr/0002-multi-tenancy-model.md) | Multi-Tenancy — shared DB, logical isolation | Accepted |
| [ADR-0003](adr/0003-security-posture-and-data-sensitivity.md) | Security Posture and Data Sensitivity | Accepted (extended Sprint 2 — redaction scope) |
| [ADR-0004](adr/0004-deferred-authentication-strategy.md) | Deferred Authentication via Security Seam | Accepted |
| [ADR-0005](adr/0005-operational-super-admin-role.md) | Operational Super-Admin Role | Accepted |
| [ADR-0006](adr/0006-parent-authentication-via-pin.md) | Parent Authentication via Per-Child PIN | Accepted |

## 5. Current capabilities

_What the system can actually do right now. Updated at sprint end._

What the system can actually do as of the latest commit:

**Tenancy and access (Sprint 1):**

- **Schema:** Church, SuperAdmin, Catechist, Klass, CatechistAssignment
  tables with UUID PKs and tenant-integrity composite FKs (Flyway V1–V5)
- **Authentication (dev mode):** Header-based identity via
  `X-Dev-Super-Admin-Id` and `X-Dev-Catechist-Id`; populates both
  custom `SecurityContext` / `TenantContext` and Spring Security's
  `SecurityContextHolder`
- **Endpoints:** `POST /admin/churches`, `POST /admin/churches/{id}/catechists`,
  `POST /classes`
- **Tenant isolation:** three layers — composite FKs at the DB,
  `TenantContext.requireChurchId()` in services, and `@PreAuthorize`
  role gates at controllers

**Roster (Sprint 2):**

- **Schema:** klass.public_slug (V6), pending_registration (V7),
  child + child_safety_info (V8), parent_contact_email nullability
  relaxation for redaction (V9)
- **Slug generation:** opaque 10-char base62 slug generated server-side
  at `POST /classes`. Stored on klass, surfaced via
  `GET /classes/{id}/registration-link` as part of an assembled
  public URL
- **Public registration:** `POST /public/classes/{slug}/registrations`
  — anonymous, validated, rate-limited (per-IP, Bucket4j), captures
  server-controlled consent metadata, returns bare 201
- **Lead-side review:** `GET /pending-registrations` (summary
  projection — Tier-3 fields excluded), `POST .../{id}/approve`
  (atomic Child + ChildSafetyInfo creation), `POST .../{id}/reject`
- **LGPD redaction:** `RosterRedactionService` clears Tier-3 fields
  from safety info and (when reachable) the originating pending
  registration, while preserving the audit skeleton. Service-layer
  only — no HTTP endpoint or UI in MVP

**Test infrastructure:**

- 70 integration + unit tests against the `catechesis_test` database
- Sprint 2 quality gate covers 7 public-surface security properties
  (enumeration resistance, rate limit, cross-tenant isolation,
  information non-leakage, consent capture, approval atomicity,
  redaction audit skeleton) across 3 dedicated test files

## 6. Known limitations

_What the system explicitly does not do, that a reasonable user might
expect. Separated from "deferred" because these are intentional MVP
exclusions, not just "not yet."_

- Parents cannot log in or self-serve; all roster changes go through
  a catechist
- Photos of children are not supported (and not planned for MVP)
- No notifications (push, email, SMS) — WhatsApp remains the
  notification channel
- No real-time sync; pages refresh on navigation or manual reload
- No resource library or announcements board (v2+)
- Portuguese (pt-BR) only; no i18n in MVP
- Single-parish pilot first; multi-parish onboarding is manual via
  Super-Admin
- The `@PreAuthorize` integration depends on Spring Security's
  `SecurityContextHolder`, populated alongside our custom contexts;
  removing the dual write would break role-based authorization.
- Rate-limit state is in-memory and per-instance. Restarts reset
  buckets; multi-instance deployments would need coordination.
  Sprint 4 hardening can move to Redis if needed.
- No HTTP endpoint for redaction. Service is invocable via test or
  one-off script; UI deferred until audit logging exists (Sprint 4+).
- Email validation on parent submissions is permissive (Bean Validation
  default). Sufficient for MVP; catechist verifies during review.

## 7. Deferred items

_Things we've consciously postponed, with the reason. These should be
revisited periodically as real usage data arrives._

- **Real-time slot sync (SSE/WebSocket)** — polling on event pages is
  enough at this scale. Revisit if concurrency conflicts become common
  in observed data.
- **Column-level encryption for sensitive fields** — relying on
  Postgres at-rest encryption for MVP. Revisit if a parish has stricter
  compliance needs.
- **Self-service church creation** — manual onboarding is fine at
  current scale. Revisit after first 5 parishes onboarded.
- **Magic-link auth** — Google OAuth-only for MVP. Revisit if a real
  catechist cannot use Google.
- **Announcement board / resource library** — stays in WhatsApp for
  MVP. Revisit in v2 planning.
- **Attendance history / analytics** — only per-event headcount in MVP.
- **Standardized error response format.** Sprint 1 and Sprint 2 used
  `ResponseStatusException` ad-hoc. Sprint 4 will introduce
  `@ControllerAdvice` + a domain-exception hierarchy. Atomicity tests
  currently assert on propagated exceptions rather than HTTP status
  for this reason.
- **Tenant-scoped repository pattern.** Currently every repository's
  `findById` is global; tenant safety lives at the layer above.
  ADR-0002 follow-up planned for Sprint 4.
- **`GET /admin/churches`** for SuperAdmin enumeration. Open question
  deferred from S01-09 — likely Sprint 3 candidate.
- **`SecurityContext.requireCatechistId()` / `requireSuperAdminId()`**
  to mirror `TenantContext.requireChurchId()`. Currently every caller
  unwraps `Optional<UUID>` via `.orElseThrow()`. Small refactor for
  Sprint 3 start.
- **Test fixture builder for `AppProperties`.** As the record grows
  (3 nested categories today), constructor-arg lists in tests
  drift. Two test sites today; builder becomes worthwhile at four.
- **Trusted-proxy boundary for rate-limit IP extraction.**
  `X-Forwarded-For` is trusted unconditionally today; spoofable
  without a real proxy. Sprint 4 deployment hardening will define
  the boundary.
- **HTTP-exposed redaction endpoint and UI.** Deferred until audit
  logging exists (Sprint 4+) so every redaction is properly recorded.
- **Constant-time slug lookup** for enumeration timing-resistance.
  Currently relies on functional indistinguishability of responses;
  microsecond-level timing differences in Postgres lookup remain
  observable in principle. Sprint 4 hardening, if needed.
- **`pending_registration` review history endpoint.** S02-09 returns
  PENDING-only; the backlog mentioned a `?status=` parameter as a
  stretch. Deferred — let real usage tell us whether Leads want it.

## 8. Development principles

Committed practices, applied to every change:

- **Contract-first**: every endpoint's DTOs are defined in `/shared`
  (OpenAPI) before backend or frontend code is written
- **Feature-first packaging**: code is organized by business feature
  (`event/`, `child/`, etc.), not by layer (`controllers/`, `services/`)
- **Tenant-safe by default**: every tenant-scoped query filters by
  `church_id`; repositories enforce this, not individual controllers
- **Test every sensitive boundary**: every Tier-3 endpoint has an
  integration test asserting a wrong-tenant request returns 404, and
  an anonymous request returns 401
- **Lifecycle methods need integration coverage**, not just unit
  coverage. Sprint 2 found a schema/entity contradiction (V9) that
  unit tests of the lifecycle method would not have caught.
- **Flyway for every schema change**: no ad-hoc SQL; no entity
  annotation creates tables
- **Migrations are idempotent**: every `V__` migration can run on a
  fresh DB and produces a working state
- **ADRs for significant decisions**: anything that a future contributor
  would ask "why is this like this?" about
- **No PII in logs, URLs, or error messages**
- **Dev mode clearly marked**: `app.auth.mode=dev` never runs in prod;
  startup check enforces this

## 9. Repository structure (planned)
catechesis/
├── backend/            Spring Boot app
├── frontend/           TypeScript + React app
├── shared/             OpenAPI contracts, shared types
├── infra/              docker-compose, scripts, env templates
├── docs/
│   ├── adr/            Architecture Decision Records
│   ├── sprints/        Sprint backlogs and reports
│   ├── runbooks/       Operational procedures
│   ├── roadmap.md      First 5 sprints
│   └── project_church.md   This file
└── README.md
## 10. Current sprint status

- **Active sprint:** Sprint 3 (planning)
- **Sprint 3 goal (preliminary, per roadmap):** A catechist can create
  and publish an event; a parent can open the event link, bootstrap
  their device binding, claim a snack slot, and mark absence. This is
  the heart of the MVP — the original problem the system was conceived
  to solve.
- **Sprint 3 backlog:** `docs/sprints/sprint-03-backlog.md` — to be
  drafted at sprint kickoff.

### Sprint history

_Append one line per completed sprint, newest on top._

- **2026-05-02 → 2026-05-15 — Sprint 2**: Roster layer. 4 migrations
  (V6–V9), 3 new entities, 6 new endpoints (public + Lead-side),
  rate limiter, LGPD redaction service. 47 new tests including a
  7-property public-surface quality gate. All 14 backlog items closed.
- **2026-04-18 → 2026-05-01 — Sprint 1**: Tenant/access foundation. 5
  schema migrations, 5 entities, security seam, 3 endpoints, 20 tests
  including cross-tenant isolation. All 13 backlog items closed.


## 11. How to use this document

### Starting a new chat session

Paste the full content of this file as the first message (or upload it
as context). It contains enough state for an AI collaborator to resume
productively without re-deriving all design decisions.

### Starting a new dev session

Read this file top to bottom. Then read the active sprint backlog. Then
pick up where you left off — most of the context is in the code, but the
**why** lives here.

### End of sprint update checklist

- [ ] Bump `Last updated` date
- [ ] Update `Active sprint` to the next sprint number
- [ ] Move completed items from plans to "Current capabilities"
- [ ] Add any new ADRs to the ADR table
- [ ] Update "Known limitations" if the system's boundaries have shifted
- [ ] Append the completed sprint to "Sprint history" with a one-line
      outcome
- [ ] Refresh "Deferred items" with anything newly postponed

---

_This document is intentionally short. When something here gets long, it
probably wants to become its own file under `docs/` and be linked here
instead._