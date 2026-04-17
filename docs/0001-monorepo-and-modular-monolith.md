# ADR-0001: Monorepo and Modular Monolith Architecture

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Bruno (solo)

## Context

The project is an MVP built by a solo developer under corporate-network
constraints (proxy, restricted outbound access). It must ship a working
slice — backend, frontend, database schema — on a tight cadence, with
feature-by-feature delivery.

Two orthogonal decisions needed to be made up front:

1. **Repository layout**: one repo for everything, or separate repos for
   frontend, backend, and shared contracts?
2. **Runtime architecture**: one deployable, or distributed services from
   day one?

Forces at play:

- Solo developer: coordination overhead between multiple repos is pure tax,
  with no benefit
- Feature-by-feature development loop (contract → backend → frontend →
  tests): cross-cutting changes are frequent
- Corporate proxy: every additional remote (repo, registry) is a
  configuration burden
- Future extensibility: the system may, one day, grow into multiple
  services or onboard other developers
- Operational simplicity: managed Postgres + one application container is
  the cheapest, most reliable production topology at this scale

## Decision

**Single repository (monorepo)** containing backend, frontend, shared
contracts, and infrastructure configuration.

**Single deployable (modular monolith)** — one Spring Boot application
serving both the API and (optionally) the static frontend assets in
production, backed by one Postgres database.

The codebase is organized by **feature**, not by layer. Each feature
module (church, class, event, child, snack, attendance) owns its
controllers, services, repositories, and domain classes. Cross-feature
coupling is explicit and minimal.

## Consequences

### Positive

- No multi-repo coordination overhead; one PR can span contract, backend,
  frontend, and tests
- One `docker-compose.yml` brings up the full stack locally
- Migrations, deploys, and rollbacks happen as a unit
- Feature-based package layout makes future extraction into services a
  mechanical refactor, not a rewrite
- Proxy configuration applies once, not per repo

### Negative

- Repo size grows faster (acceptable for years at this scale)
- Frontend and backend CI/CD must coexist in one pipeline (mitigated by
  path-based build filters when the time comes)
- Some tools (IDE refactoring, linting) must be configured for multiple
  languages in the same tree

### Follow-ups

- Folder structure standardized in project root (see `docs/structure.md`
  when written)
- When a feature needs to become its own service, the move is: extract the
  feature package → add an HTTP or message boundary → keep the shared
  contract types. No domain rewrite required.
