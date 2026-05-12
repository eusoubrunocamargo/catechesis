# Handoff prompt — Catequese Organizada

## How to use this

Start a new chat and paste the **prompt block** below as your first
message, then attach the files listed in the **files to attach**
section. The chat will have everything it needs to be immediately
productive on Sprint 3.

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
> - Feature-by-feature delivery: design → contract → backend → tests →
>   next item. Avoid jumping ahead.
> - Push back honestly when my requests would create technical debt or
>   scope creep. Name the tradeoff and let me decide.
> - Use the `ask_user_input_v0` tool for "next step" / "what should we
>   tackle" type questions, and only when there's a real decision to
>   make.
> - Commit hygiene: Conventional Commits format
>   (`feat(scope): description (Sxx-yy)`), one logical change per commit.
>
> **Project state (end of Sprint 2):**
>
> - Spring Boot 4.0.5 + Java 17, PostgreSQL 18, Flyway, Spring Security,
>   Spring Data JPA. No Lombok on entities (didactic clarity).
> - **9 schema migrations** (V1–V9), all entities for tenancy + roster
>   layer, 9 endpoints across admin, public, and Lead-side surfaces.
> - Multi-tenant: composite FKs at the DB, `TenantContext` +
>   `SecurityContext` request-scoped seams, dev auth via
>   `X-Dev-Super-Admin-Id` / `X-Dev-Catechist-Id` headers.
>   Real Google OAuth deferred to Sprint 5.
> - **70 integration tests passing**, including the Sprint 2 quality
>   gate covering 7 public-surface security properties (enumeration
>   resistance, rate limit, cross-tenant isolation, information
>   non-leakage, consent capture, approval atomicity, redaction audit
>   skeleton).
> - **Roster layer complete:** public registration form, Lead approval
>   flow, atomic Child + ChildSafetyInfo creation, per-IP rate limit
>   on `/public/**` (Bucket4j), LGPD redaction service.
>
> **Where we are:**
>
> Just committed the Sprint 3 backlog. Sprint 3 introduces the **event
> layer + parent authentication**: the heart of the MVP. A catechist
> creates events for their class, configures a per-class snack menu,
> and parents authenticate via a 6-digit PIN to claim snack slots or
> mark absence.
>
> The Sprint 3 backlog (attached) lists 18 items in detail, including
> drafting **ADR-0006: Parent Authentication via Per-Child PIN** as the
> first item. That ADR replaces an earlier roadmap proposal of
> "device binding" — a design conversation during sprint planning
> established that the goal was friction reduction, not a specific
> security property, and a per-child PIN model serves the same end with
> less infrastructure.
>
> The full state — purpose, ADRs, conventions, sprint history, current
> capabilities — is in `project_church.md` (attached). Sprint 2's
> backlog and report (attached) show how the previous sprint actually
> unfolded, including mid-sprint decisions (V9 schema relaxation,
> @Transactional test interaction, MockMvc exception non-translation)
> worth knowing about.
>
> **What I'd like to do next:**
>
> Start Sprint 3, beginning with **S03-01** — drafting ADR-0006. This
> is different from Sprint 1 and 2's openings: the first item is
> design work, not code. Walk me through the ADR didactically, the way
> we worked through Sprint 2's items. Make sure the rationale captures
> *why* we rejected device binding and *why* per-child PIN was chosen
> over alternatives (Parent entity + shared PIN, parent CPF/phone-based
> identity, etc.). The ADR should also pin down the PIN-hashing
> algorithm — the backlog notes a real tension between BCrypt (good for
> passwords, terrible for PIN lookup performance) and SHA-256-with-pepper
> (lookup-friendly, appropriate for the threat model).
>
> Read the attached files for full context. The first message back from
> you should NOT be a complete answer — confirm you've read the
> attachments, summarize what you understand the ADR to need to cover,
> and ask any clarifying questions about the PIN-hashing or PIN
> lifecycle decisions before drafting.

---

## Files to attach

Attach these from the repo, in this order. The order matters for first
impressions — the new chat will read them top-down.

### Required (5 files)

1. **`docs/project_church.md`** — the persistent context artifact.
   The new chat should read this first; it has the project purpose,
   constraints, ADR index, conventions, and current capabilities
   through end-of-Sprint-2.

2. **`docs/sprints/sprint-03-backlog.md`** — what we're about to do.
   Includes the goal, all 18 items with detail, sequencing notes, risks,
   non-goals, and the explicit decision to defer the catechist-assignment
   row-level authorization to Sprint 4.

3. **`docs/sprints/sprint-02-backlog.md`** — what we just finished.
   Useful as the template for what Sprint 3 items should look like
   when you ask the new chat to write them up.

4. **`docs/sprints/sprint-02-report.md`** — what actually happened
   during Sprint 2, including mid-sprint decisions and surprises. The
   "Decisions made" and "Surprises" sections are particularly valuable
   — they prevent the new chat from re-litigating settled questions or
   stepping on the same rakes (e.g., the V9 schema/entity contradiction,
   the @Transactional masking, the MockMvc exception non-translation).

5. **`docs/roadmap.md`** — the five-sprint plan. Tells the new chat
   what comes after Sprint 3 and frames each sprint's purpose. Worth
   noting: the original roadmap mentions "device binding" for Sprint 3;
   the actual Sprint 3 work replaces this with PIN-based authentication
   (the rationale is in the backlog and will be in ADR-0006). If the
   roadmap conflicts with the backlog, the backlog is the source of
   truth.

### Recommended for context-heavy items (3 files)

Attach these if your Sprint 3 first prompt is going to dive into a
specific layer. Otherwise, the new chat can ask for them when needed.

6. **`docs/adr/0002-multi-tenancy-model.md`** — _strongly recommended_;
   Sprint 3 directly extends this pattern with `event`, `class_menu`,
   `snack_claim`, and `absence_mark` tables, each composite-FK'd to
   parent tenant-scoped tables.

7. **`docs/adr/0003-security-posture-and-data-sensitivity.md`** —
   _recommended_; the LGPD redaction-scope addendum is relevant
   (Sprint 3 must add `parent_pin_hash` to the redaction-preserved
   set, since it's the access credential).

8. **`docs/adr/0004-deferred-authentication-strategy.md`** —
   _recommended_; the PIN-based parent authentication is the first
   *non-Lead-side* authenticated surface. Worth re-reading the seam
   design to understand where PIN auth fits.

### Optional — only if relevant to a specific item

These are reference material for spot-checking. Don't attach them by
default; pull them in if a specific issue arises.

- **`docs/adr/0001-monorepo-and-modular-monolith.md`** — stable; no
  Sprint 3 work touches this
- **`docs/adr/0005-operational-super-admin-role.md`** — Sprint 3 doesn't
  touch SuperAdmin concerns
- **`backend/pom.xml`** — if dependency questions come up (BCrypt is in
  `spring-boot-starter-security`, so no new dependency expected)
- **`backend/src/main/resources/application.yml`** — if config questions
  come up (Sprint 3 adds at least a `app.pin.pepper` config value)
- **A specific entity / controller / migration file** — when working on
  changes that ripple into existing code (e.g., when S03-03 modifies
  the registration form, attaching `PublicRegistrationRequest.java` is
  useful as the existing shape to extend)
- **`shared/api/openapi.yaml`** — when working on endpoint contracts

---

## What NOT to attach

A few things would clutter context without adding value:

- **All ADRs at once** unless the work spans many of them. Five ADRs
  is ~10K tokens; the new chat doesn't need ADR-0001 (monorepo) or
  ADR-0005 (SuperAdmin) for Sprint 3 work.
- **`pom.xml`** by default. It's stable; the dependency choices are
  baked in. Attach only if a question about a library actually arises.
  BCrypt comes via Spring Security, so no new dependency is expected
  for S03-03.
- **The whole `backend/` source tree.** Attach specific files when
  needed, not a tarball. The chat will ask if it needs more.
- **Old chat transcripts.** The Sprint 2 report captures the relevant
  takeaways from the previous chat. The transcript itself is noise.

---

## A note on what makes this work

The handoff is designed so that:

1. The new chat **reads `project_church.md` first** and gets fully
   oriented in one document. That file is the load-bearing artifact —
   if it's accurate, everything else falls into place.

2. The Sprint 2 report's "Decisions" and "Surprises" sections
   **inoculate the new chat** against re-deriving the same answers.
   Three Sprint 2 lessons are particularly important to carry into
   Sprint 3:

   - **`@Transactional` test-class interactions are subtle.** Sprint
     3's concurrent-claim test (S03-17) will hit this directly: the
     test must NOT be `@Transactional` at class level, or the
     constraint-violation rollback assertions will be masked. Use
     `@AfterEach cleanUp()` instead. The Sprint 2 report documents
     this pattern in detail.

   - **MockMvc does not translate unhandled exceptions into HTTP
     responses** without `@ControllerAdvice` (deferred to Sprint 4).
     Sprint 3's claim endpoint (S03-14) will throw
     `DataIntegrityViolationException` on slot collisions. The test
     must use `assertThatThrownBy(...).hasRootCauseInstanceOf(...)`
     rather than `mockMvc.perform(...).andExpect(status().is5xx())`.

   - **JPA `nullable = false` is a schema hint, not runtime
     enforcement.** Sprint 2 found this when V9 dropped a NOT NULL
     constraint and the entity's `nullable = false` annotation
     became dishonest (but didn't fail). Sprint 3 may face similar
     situations if PIN columns evolve.

   Plus the Sprint 1 inoculations still apply:

   - Jackson 2 / Jackson 3 split (Hibernate 7 uses Jackson 2 under
     the hood for `@JdbcTypeCode(SqlTypes.JSON)`, but Spring uses
     Jackson 3 by default)
   - `Instant` vs `OffsetDateTime` for audit timestamps (Spring Data
     auditing requires `Instant`)
   - Lombok was deliberately removed (didactic clarity); don't
     suggest re-adding it

3. The Sprint 3 backlog **anchors expectations** — the chat knows
   exactly what's in scope and what isn't, what's sized as what, and
   what risks have been flagged. In particular:

   - **S03-13 has a real performance design question**: BCrypt is
     wrong for PIN lookup because the salt makes deterministic
     lookup impossible (every comparison requires loading all rows
     and `matches()`-ing each one, which is ~3 seconds for ~30
     children). The right answer is SHA-256-with-pepper for the
     lookup column. ADR-0006 (S03-01) captures the rationale.

   - **The two-part URL `/e/{classSlug}/{eventSlug}` is a deliberate
     departure from Sprint 2's enumeration-resistance pattern.**
     Sprint 2 used the opaque `klass.public_slug` for registration
     links; Sprint 3 uses the human-readable `klass.display_slug`
     (added in V11) for event links. The tradeoff is documented in
     the S03-04 backlog item.

   - **Catechist-assignment-based row-level authorization is
     explicitly deferred.** All new admin endpoints are Lead-only,
     matching Sprint 2's pattern. The `catechist_assignment` table
     remains unused at the application layer.

4. The closing instruction ("first message back should be confirmation,
   not solution") **prevents the common failure mode** where a fresh
   chat dumps a long answer to a question you didn't ask, based on
   misreading the attachments. This is especially important for Sprint
   3 because the first item is an *ADR draft*, not a coding task —
   the new chat must approach this as a design conversation, not as a
   code generation problem.

If a future handoff needs adjustment — say, Sprint 4 has different
tone needs because it's hardening work and frontend integration —
copy this template, edit the prompt block, and adjust the
files-to-attach list to match. The structure is reusable.

---

## Sprint 3 in one sentence

> Sprint 3 is the sprint where the system stops being a roster and
> becomes a tool that solves the original problem.

(From the Sprint 3 backlog.)