# Sprint 1 — Report

- **Sprint window:** 2026-04-18 → 2026-05-01
- **Sprint goal:** A SuperAdmin (dev auth) can create a Church and its
  first Lead Catechist; that Lead (dev auth) can create a Class — all
  with real tenant isolation and integration tests.
- **Goal achieved?** Yes — all 13 work items completed.

## Outcomes

What the system can actually do as of sprint close:

- A seeded SuperAdmin (UUID `00000000-0000-0000-0000-000000000099`) can
  authenticate via `X-Dev-Super-Admin-Id` header
- A SuperAdmin can `POST /admin/churches` to create a Church (tenant)
- A SuperAdmin can `POST /admin/churches/{id}/catechists` to bootstrap
  the first Lead for a given church
- A Lead can authenticate via `X-Dev-Catechist-Id` header
- A Lead can `POST /classes` to create a class in their own church
- Tenant isolation is enforced at three layers: composite FKs at the DB,
  service-level tenant context, and `@PreAuthorize` role gates
- 20 integration tests pass, including 6 cross-tenant isolation tests
- The data model supports Church, SuperAdmin, Catechist, Klass, and
  CatechistAssignment; entities, repositories, and Flyway migrations
  V1–V5 are committed

## Work item results

| ID | Title | Final Status | Size | Notes |
|----|-------|--------------|------|-------|
| S01-01 | InitialSecurityConfig | Done | XS | Plus the UserDetailsService exclusion fix |
| S01-02 | Flyway V2 — church | Done | S | |
| S01-03 | Flyway V3 — super_admin + catechist | Done | M | |
| S01-04 | Flyway V4 — klass + catechist_assignment | Done | M | Schema simplified mid-sprint (see Decisions) |
| S01-05 | Flyway V5 — seed dev SuperAdmin | Done | XS | |
| S01-06 | JPA entities | Done | M | Lombok removed for didactic clarity (see Decisions) |
| S01-07 | TenantContext + SecurityContext | Done | M | |
| S01-08 | DevAuthenticationFilter | Done | M | |
| S01-09 | POST /admin/churches | Done | S | |
| S01-10 | POST /admin/churches/{id}/catechists | Done | S | |
| S01-11 | POST /classes | Done | S | |
| S01-12 | Cross-tenant negative tests | Done | L | Quality gate green |
| S01-13 | Sprint close docs | Done | XS | This file |

## Decisions made during the sprint

- **Dropped `stage`, `section`, `meeting_weekday`, `start_time`,
  `reception_offset_minutes` from the `klass` table.** Realized parishes
  don't share a uniform naming taxonomy and meeting details vary enough
  to live at the event level. _No new ADR — refinement of S01-04 only._
- **Switched audit timestamp type from `OffsetDateTime` to `Instant`.**
  Spring Data auditing doesn't support `OffsetDateTime`. `Instant` is
  the more idiomatic choice for "machine timestamp" semantics anyway.
  _Documented in code comments; no ADR change._
- **Removed Lombok from entity classes.** Explicit getters/setters/
  constructors give clearer didactic value for a learning-oriented
  project. Lombok kept available in `pom.xml` for future use on logging
  and DTOs if desired.
- **Used `ResponseStatusException` for service-level HTTP errors.**
  Acknowledged technical debt — couples service to web concerns.
  Will be replaced by a proper exception hierarchy +
  `@ControllerAdvice` in Sprint 4 hardening.

## What went well

- Vertical slices delivered the way the roadmap promised — DB → entity
  → service → controller → test, repeatedly
- The composite FK pattern in V4 worked first try; cross-tenant tests
  confirmed it
- Dev environment config held up across both Windows (work) and macOS
  (home) machines after the env-var setup

## What didn't

- _(your honest answer here)_

## Surprises

- **Spring Boot 4's `UserDetailsServiceAutoConfiguration` is independent
  from `SecurityFilterChain`.** Adding `InitialSecurityConfig` did NOT
  silence the "generated security password" warning. Required adding
  `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)`.
- **`@CreatedDate` / `@LastModifiedDate` don't support `OffsetDateTime`.**
  The supported set is small (`Instant`, `LocalDateTime`, `Long`, etc.).
  Forced a type change across all entities mid-sprint.
- **Spring Boot 4 ships with Jackson 3 (`tools.jackson.databind`),
  while Springdoc still pulls Jackson 2 (`com.fasterxml.jackson`).**
  Both are on the classpath. The auto-configured `ObjectMapper` is the
  Jackson 3 one. Imports must be deliberate — the IDE will happily
  suggest the wrong one.

## Carry-over into Sprint 2

None — Sprint 1 finished its backlog cleanly.

## New follow-up items uncovered

- **Add `GET /admin/churches`** for SuperAdmin enumeration. Open
  question deferred from S01 backlog. Likely Sprint 2 candidate.
- **Standardize error response format.** Currently using
  `ResponseStatusException` ad-hoc. Sprint 4 will introduce
  `@ControllerAdvice` + custom exception hierarchy.
- **Tenant-scoped repository pattern.** Right now `findById` is global
  on every repository, with tenant safety enforced at the layer above.
  Sprint 4 / ADR-0002 follow-up to introduce a `TenantScopedRepository`
  base or Hibernate `@Filter`.

## Metrics

- Items completed / planned: 13 / 13
- Commits this sprint: _(check `git log` for the count)_
- Migrations added: 4 (V2, V3, V4, V5)
- ADRs drafted or updated: 0
- Integration tests added: 13 (3 + 4 + 5 + 6 across the controllers
  and the cross-tenant suite)