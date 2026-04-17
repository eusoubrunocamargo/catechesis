# project_church

> **Purpose of this document.** This is the single source of truth for
> the state of the project. It is designed to be the **first file read**
> when starting a new chat session, a new development day, or onboarding
> any collaborator (human or AI). It is updated at the end of every
> sprint and kept concise — long-form detail belongs in ADRs and sprint
> reports, which this document links to.

- **Last updated:** 2026-04-17 (end of Sprint 0 — planning)
- **Active sprint:** Sprint 1 (not started)
- **Repo:** _url to be added on first push_

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
- **Stack:** Java 21 + Spring Boot (backend), TypeScript + React
  framework TBD (frontend), PostgreSQL (persistence), Flyway (migrations)
- **Deployment:** single-container app + managed Postgres (target
  decided in Sprint 4)

## 4. Architectural decisions (ADRs)

| ID | Title | Status |
|----|-------|--------|
| [ADR-0001](adr/0001-monorepo-and-modular-monolith.md) | Monorepo and Modular Monolith | Accepted |
| [ADR-0002](adr/0002-multi-tenancy-model.md) | Multi-Tenancy — shared DB, logical isolation | Accepted |
| [ADR-0003](adr/0003-security-posture-and-data-sensitivity.md) | Security Posture and Data Sensitivity | Accepted |
| [ADR-0004](adr/0004-deferred-authentication-strategy.md) | Deferred Authentication via Security Seam | Accepted |
| [ADR-0005](adr/0005-operational-super-admin-role.md) | Operational Super-Admin Role | Accepted |

## 5. Current capabilities

_What the system can actually do right now. Updated at sprint end._

- **Nothing yet** — pre-implementation phase. Design and documentation
  complete through Sprint 0.

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

```
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
```

## 10. Current sprint status

- **Active sprint:** Sprint 1 (not yet started)
- **Sprint 1 goal:** Running skeleton with tenant/access layer, dev auth,
  super-admin can create a Church and a Lead Catechist
- **Sprint 1 backlog:** `docs/sprints/sprint-01-backlog.md` — to be
  created on sprint kickoff

### Sprint history

_Append one line per completed sprint, newest on top._

- _(none yet — Sprint 1 will be the first execution sprint)_

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
