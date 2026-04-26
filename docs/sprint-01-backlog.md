# Sprint 1 — Backlog

- **Sprint window:** 2026-04-18 → 2026-05-01
- **Sprint goal (one sentence):** A SuperAdmin (dev auth) can create a
  Church and its first Lead Catechist; that Lead (dev auth) can create
  a Class — all with real tenant isolation and integration tests.
- **Capacity notes:** Solo developer, evenings and weekends. Target
  velocity: ~1 L, 3 M, 4 S, 2 XS across two weeks.

## Sprint goal — detail

By the end of this sprint, running the backend locally and hitting
endpoints via Swagger UI (or curl with the `X-Dev-Catechist-Id` /
`X-Dev-Super-Admin-Id` header), someone should be able to:

1. As a seeded SuperAdmin, `POST /admin/churches` to create a church
2. As that SuperAdmin, `POST /admin/churches/{id}/catechists` to create
   the first Lead Catechist for that church
3. As that Lead, `POST /classes` to create a class under their church
4. Observe, via a second church's endpoints, that no Lead can access
   or modify data in a church they don't belong to (tenant isolation
   is real, not aspirational)

No frontend, no real auth, no parent-facing flows, no children, no
events. Those arrive in later sprints. **This sprint proves the
foundation — tenancy, security seam, migrations, feature-first
packaging — can bear weight.**

## Work items

| ID | Title | Size | Status | Notes |
|----|-------|------|--------|-------|
| S01-01 | Permissive security config (`InitialSecurityConfig`) | XS | Planned | Replaces Boot's auto-configured HTTP Basic |
| S01-02 | Flyway V2 — `church` table | S | Planned | UUID PK, created_at, display fields |
| S01-03 | Flyway V3 — `super_admin` and `catechist` tables | M | Planned | Separate tables per ADR-0005; composite FK enforces tenancy on catechist |
| S01-04 | Flyway V4 — `klass` and `catechist_assignment` tables | M | Planned | M:N assignment; composite FKs for tenant integrity |
| S01-05 | Flyway V5 — seed dev SuperAdmin | XS | Planned | Your email, so dev-auth can resolve to something |
| S01-06 | JPA entities: Church, SuperAdmin, Catechist, Klass, CatechistAssignment | M | Planned | `TenantScoped` marker interface introduced here |
| S01-07 | `TenantContext` + `SecurityContext` abstractions | M | Planned | Request-scoped; no resolver logic yet beyond the dev filter |
| S01-08 | `DevAuthenticationFilter` wired under `app.auth.mode=dev` | M | Planned | Reads `X-Dev-Catechist-Id` or `X-Dev-Super-Admin-Id` |
| S01-09 | `POST /admin/churches` (SuperAdmin only) | S | Planned | OpenAPI + controller + service + test |
| S01-10 | `POST /admin/churches/{id}/catechists` (SuperAdmin only) | S | Planned | Creates first Lead for the church |
| S01-11 | `POST /classes` (Lead only, within their church) | S | Planned | Tenant resolved from current Lead |
| S01-12 | Integration test: cross-tenant negative tests | L | Planned | **The sprint's quality gate** — if this passes, isolation is real |
| S01-13 | Update `project_church.md` and draft Sprint 1 report | XS | Planned | End of sprint |

## Item detail

### S01-01 — Permissive security config  _(XS)_

Replace Boot's auto-configured HTTP Basic with a permissive `SecurityFilterChain`
that lets every request through. This is the placeholder until the real seam
lands (S01-08), but it removes the constant "Using generated security password"
noise and makes local curl / Swagger usable without credentials.

**Done when:**
- `InitialSecurityConfig` compiles and runs
- Hitting `GET /actuator/health` returns `{"status":"UP"}` with no auth header
- File carries a prominent Javadoc comment noting it must not ship to prod

---

### S01-02 — Flyway V2: `church` table  _(S)_

First real domain table. Establishes conventions for every table that follows.

**Schema:**

```sql
CREATE TABLE church (
    id              UUID         PRIMARY KEY,
    display_name    VARCHAR(200) NOT NULL,
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

Church is not tenant-scoped; it IS the tenant. No `church_id` column
on this table.

**Done when:**
- Migration runs on a fresh DB
- Migration is idempotent (re-running a fully-migrated DB is a no-op)
- `psql` confirms the table exists with expected columns

---

### S01-03 — Flyway V3: `super_admin` and `catechist`  _(M)_

Two tables. `super_admin` is system-scoped (no `church_id`).
`catechist` is tenant-scoped.

```sql
CREATE TABLE super_admin (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(200) NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE catechist (
    id          UUID         PRIMARY KEY,
    church_id   UUID         NOT NULL,
    email       VARCHAR(200) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('LEAD', 'CATECHIST')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_catechist_church
        FOREIGN KEY (church_id) REFERENCES church(id),

    CONSTRAINT uq_catechist_church_email
        UNIQUE (church_id, email)
);

CREATE INDEX idx_catechist_church ON catechist(church_id);
```

**Key decision:** `church_id` on catechist participates in every query.
No queries of the form `SELECT * FROM catechist WHERE id = ?` —
always `WHERE id = ? AND church_id = ?`.

**Done when:**
- Both tables migrated
- FK verified by attempting (manually, via psql) to insert a catechist
  with a non-existent `church_id` — should fail

---

### S01-04 — Flyway V4: `klass` and `catechist_assignment`  _(M)_

`klass` is the avoidance of the SQL reserved word `class`. Same
naming in Java package, entity, and file name — boring and consistent.

`catechist_assignment` is the M:N between catechist and klass.

```sql
CREATE TABLE klass (
    id          UUID         PRIMARY KEY,
    church_id   UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    stage       VARCHAR(100) NOT NULL,
    section     VARCHAR(20),
    meeting_weekday SMALLINT NOT NULL,
    start_time  TIME         NOT NULL DEFAULT '09:00',
    reception_offset_minutes INTEGER NOT NULL DEFAULT 20,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_klass_church
        FOREIGN KEY (church_id) REFERENCES church(id)
);

CREATE INDEX idx_klass_church ON klass(church_id);

-- Composite unique indexes required for the FKs below
ALTER TABLE catechist ADD CONSTRAINT uq_catechist_id_church
    UNIQUE (id, church_id);
ALTER TABLE klass ADD CONSTRAINT uq_klass_id_church
    UNIQUE (id, church_id);

CREATE TABLE catechist_assignment (
    id            UUID         PRIMARY KEY,
    church_id     UUID         NOT NULL,
    catechist_id  UUID         NOT NULL,
    klass_id      UUID         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_assignment_catechist
        FOREIGN KEY (catechist_id, church_id)
        REFERENCES catechist(id, church_id),
    CONSTRAINT fk_assignment_klass
        FOREIGN KEY (klass_id, church_id)
        REFERENCES klass(id, church_id),

    CONSTRAINT uq_assignment
        UNIQUE (catechist_id, klass_id)
);
```

**The composite FK pattern is load-bearing.** It makes cross-tenant
data corruption physically impossible at the DB level. Get it right
once, reuse the pattern everywhere.

**Done when:**
- Tables migrated
- Composite FK tested manually: try to insert an assignment where
  `catechist.church_id ≠ klass.church_id` — the insert must fail

---

### S01-05 — Seed dev SuperAdmin  _(XS)_

A Flyway migration that inserts a single `super_admin` row with your
email. Required because the `DevAuthenticationFilter` needs to resolve
a `X-Dev-Super-Admin-Id` header to an actual DB row.

```sql
-- V5__seed_dev_super_admin.sql
INSERT INTO super_admin (id, email, name)
VALUES (
    '00000000-0000-0000-0000-000000000099',
    'brunocamargo@example.com',
    'Bruno (dev)'
);
```

UUID is fixed so you can hardcode it in dev-only scripts and curl
examples. Acceptable only because it's a dev seed.

**Done when:**
- Migration runs
- Row visible in psql

---

### S01-06 — JPA entities  _(M)_

Five entities matching the five tables.

```java
public interface TenantScoped {
    UUID getChurchId();
}
```

Every tenant-scoped entity implements this. `Church` itself does NOT
— it's the tenant, not tenant-scoped. `SuperAdmin` does NOT — it's
system-level.

**Key decisions:**
- `@Id` with application-generated `UUID` (UUIDv7 if available; else
  `UUID.randomUUID()`)
- Entity setters where mutation is truly needed; prefer constructors +
  getters for value-object-like fields
- Don't use Lombok `@Data` on entities (breaks JPA `equals`/`hashCode`)
- Records for DTOs; classes for entities
- `@Column(nullable = false)` matching DB schema — Hibernate `validate`
  will catch drift

**Done when:**
- All five entities compile
- App boots cleanly with `ddl-auto: validate`
- Basic Spring Data JPA repositories (`ChurchRepository`, etc.) exist
  with no custom methods yet

---

### S01-07 — `TenantContext` + `SecurityContext` abstractions  _(M)_

The two seams all future code depends on. Keep small.

```java
public interface SecurityContext {
    Optional<UUID> currentCatechistId();
    Optional<UUID> currentSuperAdminId();
    Optional<UUID> currentChurchId();
    Optional<CatechistRole> currentRole();
    boolean isAuthenticated();
    boolean isSuperAdmin();
}

public interface TenantContext {
    UUID requireChurchId();
    Optional<UUID> churchIdOptional();
}
```

Request-scoped bean implementations populated by the
`DevAuthenticationFilter` (S01-08). Later, OAuth2 populates the same
beans.

**Done when:**
- Abstractions defined in `common/security` and `common/tenancy`
- Request-scoped implementations registered
- A trivial `@RestController` can inject them and return the current
  catechist ID for manual testing

---

### S01-08 — `DevAuthenticationFilter`  _(M)_

The real security seam's dev implementation. Reads two
mutually-exclusive headers:

- `X-Dev-Super-Admin-Id: <uuid>` — resolves via `super_admin` table
- `X-Dev-Catechist-Id: <uuid>` — resolves via `catechist` table;
  populates `currentChurchId` from that row

Filter behavior:
- Both headers present → 400 Bad Request
- Neither → leave SecurityContext empty (anonymous)
- Valid header → populate SecurityContext + TenantContext
- Invalid UUID format → 400
- Valid UUID but no matching row → 401

Active only when `app.auth.mode=dev`.

**Done when:**
- Filter registered in the Spring Security filter chain
- Three happy-path tests: anonymous, super-admin header, catechist header
- Two negative tests: invalid UUID format, unknown UUID
- Startup log line `DEV AUTH MODE ACTIVE` stands out

---

### S01-09 — `POST /admin/churches`  _(S)_

First real endpoint. SuperAdmin-only. Creates a Church.

**Flow:**
1. Spec the endpoint in `shared/api/openapi.yaml` (request/response DTOs)
2. Controller: `ChurchController` in the `church` package
3. Service: `ChurchService.createChurch(CreateChurchRequest)`
4. Authorization: super-admin check at the service or annotation layer
5. Return the created Church as a DTO — never the entity
6. Integration test: SuperAdmin → 201; Catechist → 403; anonymous → 401

**Done when:**
- Endpoint visible in Swagger UI
- All three tests pass

---

### S01-10 — `POST /admin/churches/{id}/catechists`  _(S)_

SuperAdmin-only. Creates the first Lead Catechist for a given church.

**Rules:**
- Church must exist (else 404)
- Email must be valid
- If an active Lead already exists → 409 Conflict (the "first Lead"
  flow is super-admin-only, single-use per church; later Leads come
  from Lead-to-Lead invitation, future sprint)

**Done when:**
- Endpoint works, Swagger UI reflects it
- Tests cover: happy path, nonexistent church, duplicate Lead, non-super-admin

---

### S01-11 — `POST /classes`  _(S)_

Lead-only. Lead's church is inferred from the authenticated catechist.

**Validation:**
- Requester must be a Lead (not a regular Catechist in MVP)
- `name`, `stage`, `meetingWeekday` required
- `startTime`, `receptionOffsetMinutes` have defaults

**Done when:**
- Endpoint works
- Tests: happy path, non-Lead catechist, validation errors

---

### S01-12 — Integration test: cross-tenant negative tests  _(L)_

**The most important item of the sprint.** If this fails, everything
else is a lie.

Scenario:

1. Seed two churches — A and B — with their respective Leads
2. Lead of A tries to:
    - `POST /classes` for their own church → succeeds
    - `GET /classes/{id}` where `{id}` belongs to B → 404 (not 403 — we
      pretend it doesn't exist to the wrong tenant)
    - `PATCH /classes/{id}` where `{id}` belongs to B → 404
3. Lead of B cannot see anything created by Lead of A
4. SuperAdmin can enumerate churches but NOT read tenant data without
   impersonation (out of MVP scope)

**Done when:**
- At least six distinct negative assertions pass
- `./mvnw test` green
- Commenting out a repository `church_id` filter causes the test to
  fail (manual sanity check)

---

### S01-13 — Close-of-sprint docs  _(XS)_

- Update `docs/project_church.md`:
    - Last updated date
    - Current capabilities list (three endpoints, dev auth, tenant
      isolation)
    - Current sprint = Sprint 2
    - Append a line to Sprint History
- Draft `docs/sprints/sprint-01-report.md` from the template

## Risks & unknowns

- **Hibernate 7 behavior with composite FKs.** Standard SQL but
  Spring Data JPA's support can be awkward. Fallback: application-level
  tenant assertions in the repository — still safe, slightly less
  bulletproof.
- **UUIDv7 library availability.** If no clean library is on Maven
  Central for Java 17, fall back to `UUID.randomUUID()` (UUIDv4).
  Performance is fine at this scale.
- **Springdoc-openapi with Spring Boot 4.** Version 3.0.2 is new; if
  Swagger UI breaks, demote it and use curl until a fix ships.
- **`@PreAuthorize` with a custom `SecurityContext` bean.** Spring's
  standard `@PreAuthorize` integrates with its own `Authentication`,
  not our custom seam. Bridge options: wrap our context in a fake
  `Authentication`, or use method-level guard code. Small but non-zero
  investigation.

## Explicitly NOT in this sprint

- Parent-facing endpoints (Sprint 2)
- Google OAuth (Sprint 5)
- Any frontend code
- Audit logging (Sprint 4)
- Redaction flow for LGPD erasure (Sprint 4)
- Events, snacks, attendance (Sprint 3)
- Multi-Lead management / invitations
- Rate limiting / abuse protection
- Email / notifications (not in MVP at all)

## Carry-over from Sprint 0

None — Sprint 0 was planning/scaffolding only.

## Open questions to resolve during the sprint

- Should SuperAdmin list Churches? Almost certainly yes. Add
  `GET /admin/churches` if time permits; otherwise defer.
- Standardize error response shape on RFC 7807
  `application/problem+json`? (Spring Boot 4 default; I'd adopt it.)
  Make concrete with the first error case.
- Logging in dev mode: at minimum the startup `DEV AUTH MODE ACTIVE`
  banner. Request-level DEBUG logging is probably enough.