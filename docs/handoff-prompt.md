# Handoff prompt — Catequese Organizada

## How to use this

Start a new chat and paste the **prompt block** below as your first
message, then attach the files listed in the **files to attach**
section. The chat will have everything it needs to be immediately
productive on Sprint 2.

---

## Prompt block — copy-paste this verbatim

> I'm continuing development of **Catequese Organizada**, a multi-tenant
> Spring Boot 4 application for managing Catholic parish catechesis
> classes (Brazilian Portuguese context). It's a didactic, learning-
> oriented project — I'm a Java developer, working solo, evenings and
> weekends, across two machines: a Windows corporate setup (behind
> proxy, no Docker) and a macOS home setup (no proxy).
>
> **Approach to keep using:**
>
> - Didactic walkthroughs: high-level shape first, then specifics, with
>   reasoning for non-obvious choices. Don't dump finished code without
>   explaining the decisions behind it.
> - Feature-by-feature delivery: contract → backend → tests → next
>   item. Avoid jumping ahead.
> - Push back honestly when my requests would create technical debt or
>   scope creep. Name the tradeoff and let me decide.
> - Use the `ask_user_input_v0` tool for "next step" / "what should we
>   tackle" type questions, and only when there's a real decision to
>   make.
> - Commit hygiene: Conventional Commits format
>   (`feat(scope): description (Sxx-yy)`), one logical change per commit.
>
> **Project state (end of Sprint 1):**
>
> - Spring Boot 4.0.5 + Java 17, PostgreSQL 18, Flyway, Spring Security,
>   Spring Data JPA. No Lombok on entities (didactic clarity).
> - 5 schema migrations (V1–V5), 5 entities (Church, SuperAdmin,
>   Catechist, Klass, CatechistAssignment), 3 endpoints
>   (`POST /admin/churches`, `POST /admin/churches/{id}/catechists`,
>   `POST /classes`).
> - Multi-tenant: composite FKs at the DB, `TenantContext` +
>   `SecurityContext` request-scoped seams, dev auth via
>   `X-Dev-Super-Admin-Id` / `X-Dev-Catechist-Id` headers.
>   Real Google OAuth deferred to Sprint 5.
> - 20 integration tests passing, including 6 cross-tenant isolation
>   tests as the Sprint 1 quality gate.
>
> **Where we are:**
>
> Just committed the Sprint 2 backlog. Sprint 2 introduces the **roster
> layer**: `PendingRegistration`, `Child`, `ChildSafetyInfo`, the first
> public unauthenticated endpoint (parent registration form), Lead
> approval flow, and an LGPD redaction service.
>
> The full state — purpose, ADRs, conventions, sprint history, current
> capabilities — is in `project_church.md` (attached). The Sprint 2
> backlog (attached) lists the 14 items in order. The Sprint 1 backlog
> and report (attached) show how the previous sprint actually unfolded,
> including mid-sprint decisions and surprises worth knowing about.
>
> **What I'd like to do next:**
>
> Start Sprint 2, beginning with **S02-01** (Flyway V6 — `klass.public_slug`
> column + backfill). Walk me through it didactically the way we worked
> through Sprint 1's items: design decisions first, then code, then
> verification.
>
> Read the attached files for full context. The first message back from
> you should NOT be a complete answer — confirm you've read the
> attachments, summarize what you understand the next step to be, and
> ask any clarifying question if anything in `project_church.md` or the
> Sprint 2 backlog seems ambiguous to you.

---

## Files to attach

Attach these from the repo, in this order. The order matters for first
impressions — the new chat will read them top-down.

### Required (5 files)

1. **`docs/project_church.md`** — the persistent context artifact.
   The new chat should read this first; it has the project purpose,
   constraints, ADR index, conventions, and current capabilities.

2. **`docs/sprints/sprint-02-backlog.md`** — what we're about to do.
   Includes the goal, all 14 items with detail, risks, and explicit
   non-goals.

3. **`docs/sprints/sprint-01-backlog.md`** — what we just finished.
   Useful as the template for what Sprint 2 items should look like
   when you ask the new chat to write them up.

4. **`docs/sprints/sprint-01-report.md`** — what actually happened
   during Sprint 1, including mid-sprint decisions, surprises, and
   tech debt taken on. The "Decisions made" and "Surprises" sections
   are particularly valuable — they prevent the new chat from
   re-litigating settled questions or stepping on the same rakes.

5. **`docs/roadmap.md`** — the five-sprint plan. Tells the new chat
   what comes after Sprint 2 and frames each sprint's purpose.

### Recommended for context-heavy items (5 files)

Attach these if your Sprint 2 first prompt is going to dive into a
specific layer that the new chat needs to understand intimately.
Otherwise, the new chat can ask for them when needed.

6. **`docs/adr/0001-monorepo-and-modular-monolith.md`**
7. **`docs/adr/0002-multi-tenancy-model.md`** — _strongly recommended_;
   Sprint 2 directly extends this pattern with new tenant-scoped tables.
8. **`docs/adr/0003-security-posture-and-data-sensitivity.md`** —
   _strongly recommended_; Sprint 2 introduces tier-3 sensitive data
   (allergies, emergency contacts), and the redaction flow comes
   straight from this ADR.
9. **`docs/adr/0004-deferred-authentication-strategy.md`**
10. **`docs/adr/0005-operational-super-admin-role.md`**

### Optional — only if relevant to a specific item

These are reference material for spot-checking. Don't attach them by
default; pull them in if a specific issue arises.

- **`backend/pom.xml`** — if dependency questions come up
- **`backend/src/main/resources/application.yml`** — if config
  questions come up
- **A specific entity / controller / migration file** — when working
  on changes that ripple into existing code (e.g., when S02-03 adds a
  `Child` entity, attaching `Catechist.java` is useful as the pattern
  to follow)
- **`shared/api/openapi.yaml`** — when working on endpoint contracts
  (S02-06 onwards)

---

## What NOT to attach

A few things would clutter context without adding value:

- **All ADRs at once** unless the work spans many of them. Five ADRs
  is ~10K tokens; the new chat doesn't need ADR-0001 (monorepo) when
  doing a Flyway migration.
- **`pom.xml`** by default. It's stable; the dependency choices are
  baked in. Attach only if a question about a library actually arises.
- **The whole `backend/` source tree.** Attach specific files when
  needed, not a tarball. The chat will ask if it needs more.
- **Old chat transcripts.** The Sprint 1 report captures the relevant
  takeaways from the previous chat. The transcript itself is noise.

---

## A note on what makes this work

The handoff is designed so that:

1. The new chat **reads `project_church.md` first** and gets fully
   oriented in one document. That file is the load-bearing artifact —
   if it's accurate, everything else falls into place.
2. The Sprint 1 report's "Decisions" and "Surprises" sections
   **inoculate the new chat** against re-deriving the same answers.
   The Jackson 3 / Jackson 2 trap, the `Instant` vs `OffsetDateTime`
   issue, the Lombok-removal call — all are documented, so the new
   chat won't suggest them again.
3. The Sprint 2 backlog **anchors expectations** — the chat knows
   exactly what's in scope and what isn't, what's sized as what, and
   what risks have been flagged.
4. The closing instruction ("first message back should be confirmation,
   not solution") **prevents the common failure mode** where a fresh
   chat dumps a long answer to a question you didn't ask, based on
   misreading the attachments.

If a future handoff needs adjustment — say, Sprint 3 has different
tone needs because it's frontend work — copy this template, edit the
prompt block, and adjust the files-to-attach list to match. The
structure is reusable.