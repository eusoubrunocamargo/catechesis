# ADR-0005: Operational Super-Admin Role

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Bruno (solo)

## Context

The system supports multiple tenants (churches). A tenant is created by
registering a `Church` row and seeding its first Lead Catechist. Two
questions arise:

1. **Who creates a Church?** Self-service ("any user can start a new
   church") risks abuse, spam, and accidental data mixing, none of which
   the MVP can afford to handle.
2. **Who is the first user of a new Church?** A bootstrap problem —
   someone must exist before the Lead Catechist does, to create them.

A realistic MVP operational model — email Bruno to set up your parish —
is cheap, safe, and correct for the scale we expect (handful of parishes
per year). But the *data model* must make this role explicit from day
one; improvising a "developer has special access" mechanism later will
result in inconsistent, untested code paths.

## Decision

Introduce a **Super-Admin** role as a first-class entity, separate from
catechists:

- A dedicated `super_admin` table, not a flag on `catechist`
- Not scoped to any church; the Super-Admin is system-level
- Authenticates via the same Google OAuth flow as catechists (see
  ADR-0004), but resolves to `SuperAdmin` rather than `Catechist`
- Has the following capabilities in the API:
  - Create a `Church`
  - Create the first `Catechist` with `LEAD` role for a new church
  - Deactivate or delete a church (rare, audited)
  - Read any `AuditEvent` across tenants
- Does **not** automatically see tenant data; any access to tenant-scoped
  records requires an explicit, audited action ("impersonate for support")
- In MVP, does not have a UI beyond the login page and the
  church-creation form — all other work is done via admin API endpoints
  or direct DB scripts

### Onboarding flow (MVP)

1. Operator (the Super-Admin) receives a request ("please set up
   Paróquia X")
2. Operator logs in via Google OAuth with an email registered in
   `super_admin`
3. Calls the admin endpoint to create the Church
4. Calls the admin endpoint to create the first Lead Catechist, providing
   their email and name
5. The Lead logs in via Google OAuth with that email and is recognized
6. The Lead takes over: creates classes, invites other catechists,
   publishes registration links

### Bootstrap

The very first Super-Admin row is created by a **Flyway migration** that
inserts a specific email (the developer's). This is the only
pre-seeded row in the system. New Super-Admins (if ever) are added by
existing Super-Admins via admin API.

## Consequences

### Positive

- Privilege boundary is visible in the schema (`super_admin` table vs
  `catechist` table), not hidden in a role enum
- Impossible to accidentally grant a catechist super-admin powers via a
  field update
- Matches the realistic operational model: one operator, small number of
  tenants, manual onboarding
- Future self-service church creation (if ever warranted) is a purely
  additive change — add a new API, keep the role
- Clean separation in queries: `findAllCatechists()` never surprises you
  with a super-admin

### Negative

- One extra table and one extra login-resolution branch
- Super-Admin management has no UI in MVP; all done via API calls or SQL
  (acceptable for solo operator)
- If the operator's Google account is lost/compromised, recovery requires
  direct DB access (documented runbook mitigates this)

### Follow-ups

- Runbook: `docs/runbooks/bootstrap-super-admin.md` describing how to
  seed the first Super-Admin and how to recover if the email changes
- Runbook: `docs/runbooks/onboard-parish.md` describing the end-to-end
  parish onboarding steps
- Integration test: a Super-Admin can create a Church; a Lead Catechist
  cannot; a non-admin catechist cannot
- Integration test: a Super-Admin does **not** see child data from any
  tenant unless explicitly impersonating (impersonation not in MVP scope)
