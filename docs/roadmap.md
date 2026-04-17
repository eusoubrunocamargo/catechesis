# Roadmap — First Five Sprints

This is the **initial plan**, written with the clarity available on day
zero. Sprints will be re-planned at their start; later sprints will
inevitably shift based on what sprints 1–2 teach us. The goal of this
document is not to predict the future; it is to validate that the path
we have in mind is coherent and to surface risks early.

Each sprint delivers a **vertical slice** — DB migration → backend
endpoint → frontend page → tests — rather than a horizontal layer.

---

## Sprint 1 — Foundations

**Window:** 2 weeks
**Goal:** A running skeleton where the Super-Admin can create a Church,
create a Lead Catechist, and the Lead can create a Class. All in dev-auth
mode, with tenancy and the security seam correctly wired.

### Main outcomes

- Monorepo scaffolded per `docs/structure.md` convention
- Backend: Spring Boot project, Postgres via docker-compose, Flyway
  migrations running
- Frontend: Next.js (or Vite+React — decided in this sprint) app running
  against the backend
- OpenAPI contract skeleton in `/shared`
- **Domain model — tenant/access layer only**: `Church`, `SuperAdmin`,
  `Catechist`, `Class`, `CatechistAssignment`
- `TenantContext` and `SecurityContext` abstractions in place, backed by
  `DevAuthenticationFilter`
- Three working endpoints:
  - `POST /admin/churches` (super-admin)
  - `POST /admin/churches/{id}/catechists` (super-admin, creates Lead)
  - `POST /classes` (lead catechist)
- Minimal UI: a "dev login" page, super-admin dashboard, lead dashboard
- Integration test per endpoint, including tenant-isolation negative test

### Technical risks addressed

- Proxy-restricted network: all dependencies resolvable, Docker images
  cached, Flyway running locally
- Tenancy enforcement wired correctly from day one (harder to retrofit
  than to build in)
- Security seam proven swappable (dev filter populates context exactly
  as the future Google filter will)

### Explicitly NOT in this sprint

- Authentication via Google OAuth (deferred per ADR-0004)
- Parent-facing flows
- Snack lists, events, children
- Styling beyond "functional and legible"

---

## Sprint 2 — Roster (Children & Registrations)

**Window:** 2 weeks
**Goal:** A Lead Catechist can generate a per-class registration link, a
parent can submit a registration via that link, and the catechist can
approve or reject it, promoting it to an active Child.

### Main outcomes

- Domain model — roster layer: `PendingRegistration`, `Child`,
  `ChildSafetyInfo`
- Public endpoint: `POST /public/classes/{slug}/registrations` (no auth,
  rate-limited, tenant resolved from slug)
- Admin endpoints: list pending, approve, reject
- Public registration form (parent-facing, mobile-first, pt-BR)
- Catechist dashboard: pending registrations list, review view
- Consent text + checkbox + version recording
- Redaction mechanism for LGPD right of erasure (data model + service,
  no UI yet)
- Integration tests: cross-tenant isolation on registrations, approval
  promotes to Child, rejected registration cannot claim snacks (anticipating
  Sprint 3)

### Technical risks addressed

- Public endpoint without auth: rate limiting, input validation, and
  abuse resistance proven viable
- Tier-3 data (safety info) properly isolated in its own table and
  service method

### Explicitly NOT in this sprint

- Events
- Snack lists
- Device binding (comes with events)
- Photos (deferred, per ADR-0003)

---

## Sprint 3 — Events & Snack Lists (Core MVP)

**Window:** 2 weeks
**Goal:** A catechist can create and publish an event; a parent can open
the event link, bootstrap their device binding, claim a snack slot, and
mark absence. This is the heart of the MVP.

### Main outcomes

- Domain model — event/participation layer: `Event`, `SnackCategory`,
  `SnackSlot`, `SnackClaim`, `AbsenceMark`, `DeviceClassBinding`
- Event lifecycle: DRAFT → OPEN → LOCKED → ARCHIVED, with auto-lock and
  auto-archive scheduled jobs
- Slot uniqueness enforced at DB level via unique constraint
- Admin flow: create event, configure categories/slots, publish, lock
- Parent flow: open link, first-time bootstrap, claim slot, mark absence,
  confirmation popup naming the child ("Do you confirm Gael will bring…")
- Short event URL format: `/e/{eventId}` — eventId is UUIDv7
- Catechist dashboard view: consolidated snack list + expected headcount
- End-to-end manual validation against a real catechesis Sunday scenario
  (simulated, with seed data resembling the WhatsApp history)

### Technical risks addressed

- Concurrency — two parents claiming the same slot — fully handled by
  DB-level uniqueness, verified by a concurrent-claim integration test
- Device binding pattern validated in practice; edge cases (second
  device, cleared cookies) documented
- Event state transitions atomic; auto-lock job tested

### Explicitly NOT in this sprint

- Real-time sync (polling is enough; SSE/WebSocket deferred)
- Rich event notes / per-event announcements beyond a plain text note
- Reopening an archived event

---

## Sprint 4 — Hardening & Operations

**Window:** 2 weeks
**Goal:** The MVP is deployable, observable, and safe enough to run in
front of one real parish. This is the "not a feature sprint" sprint —
every engineer has one, and skipping it is how projects die.

### Main outcomes

- Real deployment target chosen and configured (likely: managed Postgres
  + a single container host — Fly.io, Railway, or a modest VPS)
- TLS configured end-to-end (Let's Encrypt in prod, mkcert in local)
- Audit log entity + writes on all sensitive actions (approve
  registration, read safety info, state transitions)
- Structured logging; no PII in logs; log aggregation somewhere readable
- Error tracking (Sentry free tier or similar)
- Backups: managed Postgres snapshots, documented restore drill
- Startup safety checks (prod profile refuses dev auth mode, etc.)
- Runbooks:
  - `docs/runbooks/bootstrap-super-admin.md`
  - `docs/runbooks/onboard-parish.md`
  - `docs/runbooks/restore-from-backup.md`
- Redaction flow has a UI (super-admin only, behind a confirmation)

### Technical risks addressed

- Deployability proven; first real-world constraint discovered
- Recovery path exists and has been tested
- Operational observability good enough to diagnose the first support
  call

### Explicitly NOT in this sprint

- Google OAuth (Sprint 5)
- Multi-tenant UI niceties (one-tenant-at-a-time is fine)
- Performance optimization (we have 0 users)

---

## Sprint 5 — Real Authentication & First Pilot

**Window:** 2 weeks
**Goal:** Replace the dev auth filter with Google OAuth. Onboard the
first real parish. Observe.

### Main outcomes

- `GoogleOAuth2AuthenticationFilter` implemented and active in staging
  and prod
- Session handling via Spring Session on Postgres
- Login flow tested across desktop and mobile (including in-app WebView
  edge cases)
- Startup safety check asserting `app.auth.mode=google` in prod
- One real parish onboarded following the runbook
- At least one real catechist logged in, created a class, generated a
  registration link, processed real (or realistic-test) registrations
- Parent-facing event link tested in the actual WhatsApp group context

### Technical risks addressed

- OAuth integration works from prod environment (may not work from
  corporate dev network; that's fine — staging is the proving ground)
- The security seam's second implementation proves the seam design was
  correct
- Real-world mobile UX validated

### Explicitly NOT in this sprint

- Announcements feature (v2)
- Photos (deferred indefinitely until a real use case appears)
- Magic-link auth as fallback (only if OAuth proves insufficient)
- Multi-parish simultaneous pilot (one at a time; learn before scaling)

---

## What comes after Sprint 5

Not planned in detail. Candidates, in rough priority:

- Announcements / meeting minutes (v2)
- Multi-parish onboarding at a modest pace
- Catechist-to-catechist invitation flow (Lead invites regular catechists
  without super-admin involvement)
- Parent correction-request form
- Export (PDF) of an event's snack list for catechist field use

Each of these should be re-evaluated after Sprint 5's real-world
feedback. The most valuable thing Sprint 5 produces is probably not the
code — it's the list of things we discover we were wrong about.
