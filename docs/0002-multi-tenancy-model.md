# ADR-0002: Multi-Tenancy Model — Shared Database, Logical Isolation

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Bruno (solo)

## Context

The system is designed to host multiple independent groups (parishes,
churches) on a single deployment. Each tenant — identified as a `Church` —
owns its own classes, catechists, children, and events. Tenant data must
never leak across boundaries.

Tenants will be onboarded manually (see ADR-0005) and remain small in
absolute terms (dozens of parishes, not thousands) for the foreseeable
future.

Candidate isolation strategies:

1. **Database-per-tenant** — one Postgres database per church
2. **Schema-per-tenant** — one Postgres schema per church in a shared DB
3. **Row-level (logical) isolation** — a shared schema, every tenant-scoped
   table carries a `church_id` column, and every query filters on it

Forces at play:

- Tenant count will remain low (10s, not 1000s) for years
- Solo developer operating the system — migration complexity is a
  first-order concern
- Managed Postgres pricing is instance-based, not DB-based — per-tenant DBs
  are wasteful
- Cross-tenant operational queries (by the Super-Admin) should be trivial
- The cost of a tenant-isolation bug is high (exposure of child safety data)

## Decision

**Shared database, row-level isolation via a `church_id` column on every
tenant-scoped table.**

Isolation is enforced at three layers:

1. **Data model** — every tenant-scoped entity implements a `TenantScoped`
   marker interface and carries `churchId`. Composite foreign keys
   `(x_id, church_id) → (id, church_id)` prevent cross-tenant references
   at the database level.
2. **Repository** — a `TenantScopedRepository` base (or equivalent
   Hibernate filter) injects `church_id = :currentTenant` into every
   query. Developers cannot forget to filter.
3. **Request context** — a `TenantContext` bean, populated at request
   entry by a servlet filter, supplies the current `church_id`. Resolved
   from the authenticated catechist (once auth exists) or from the
   URL-embedded resource (for public event/registration links).

URLs use **implicit tenancy** — `/classes/{classId}/events/{eventId}`,
not `/churches/{churchId}/classes/{classId}/...`. The church is derived
server-side from the resource. Responses include `churchId` so clients can
assert consistency.

Entities that are NOT tenant-scoped (`Church` itself, `SuperAdmin`,
system-level audit records) explicitly do not carry `church_id` and do
not implement `TenantScoped`.

## Consequences

### Positive

- Simple operational model: one DB, one backup, one connection pool
- Easy cross-tenant queries for the operator (no aggregation across DBs)
- Migrations run once, atomically, across all tenants
- Cheap to add tenants (one row in `church`, one row in `catechist`)
- Three enforcement layers make isolation bugs hard to introduce

### Negative

- A bug in the repository layer could expose all tenants (mitigated by:
  integration tests per repository asserting cross-tenant isolation)
- No physical guarantee of tenant isolation — depends on correct code
- Upgrading one tenant to a different feature set (e.g. opt-in to a beta
  feature) requires per-row flags, not per-DB config
- Restoring a single tenant's data from backup is non-trivial (must
  filter on `church_id` from a full DB dump)

### Follow-ups

- Every integration test for a tenant-scoped repository must include a
  negative test: "entity from tenant B is not visible to tenant A."
- The `TenantContext` abstraction must be created on day one so it can
  evolve from dev-header-based resolution to auth-based resolution without
  touching downstream code.
- If ever a parish demands a physical DB of its own (regulatory or
  political reason), the migration path is: export all rows where
  `church_id = X`, import into a dedicated DB, flip a config flag that
  routes that tenant's connections to the new DB. Non-trivial but
  bounded.
