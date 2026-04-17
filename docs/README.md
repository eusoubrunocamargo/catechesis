# Sprint Process

A lightweight, documentation-first sprint process for a solo developer
working in short iterative cycles. The goal is **discipline without
tooling overhead**: all sprint artifacts live in the repo as markdown,
version-controlled alongside the code they describe.

## Principles

1. **Text over tools.** No Jira, no Trello, no Linear. A text file in
   the repo is the source of truth. This survives tool migrations,
   context switches, and new chat sessions.
2. **Short sprints.** Two weeks is the default. Shorter is fine;
   longer drifts.
3. **Vertical slices.** Every sprint delivers at least one end-to-end
   slice (DB → API → UI), not a horizontal layer cake.
4. **Evidence-based done.** A task is done when it has a test, a commit,
   and the `project_church` artifact updated. No "done-done" vs "done."
5. **Write for the future reader.** Everything written during a sprint
   should make sense to someone (or some LLM) opening the repo six months
   later, cold.

## Artifacts

Two living documents per sprint:

- `docs/sprints/sprint-NN-backlog.md` — the plan, written at sprint start
- `docs/sprints/sprint-NN-report.md` — the retrospective, written at end

Plus a persistent global artifact:

- `docs/project_church.md` — the project state snapshot, updated at the
  end of every sprint (see `project_church.md` itself for details)

## Sprint lifecycle

### 1. Sprint Planning (Day 0, ~30 min)

- Review `project_church.md` to refresh state
- Review the previous sprint's `Carry-over` section
- Pick 3–6 work items for the next sprint (see **Sizing** below)
- Create `sprint-NN-backlog.md` from the template

### 2. Daily Work

- Work on items in priority order
- Keep the backlog file open; update status inline as work progresses
- When blocked, write a `BLOCKED:` note in the item — don't rely on memory
- Commits reference item IDs: `feat(event): add auto-lock job (S03-04)`

### 3. Sprint Close (Day N, ~20 min)

- Mark all items as `Done`, `Carried Over`, `Dropped`, or `Deferred`
- Write `sprint-NN-report.md` from the template
- Update `project_church.md` sections: Current Sprint Status, Current
  Capabilities, Known Limitations, and any new ADR references
- Commit everything with message `chore(sprint): close sprint NN`

## Sizing

Use **T-shirt sizes**, not hour estimates. Estimates lie; shapes don't.

- **XS** (<2h) — trivial fix, simple config, tiny test
- **S** (half-day) — one well-bounded feature slice
- **M** (1–2 days) — a feature with non-trivial integration
- **L** (3+ days) — a feature that probably wants to be split
- **XL** — don't. Split it.

A healthy sprint carries roughly: 1 L, 2–3 M, 2–3 S, and leaves 20% of
capacity unplanned for surprises. If every sprint fills 100%, velocity
predictions are lying.

## Status values

Every backlog item has exactly one status at any time:

- `Planned` — not started
- `In Progress` — active work, expect daily commits
- `Blocked` — can't proceed; write the reason inline
- `In Review` — code done, tests passing, awaiting a final self-review
  pass or integration test
- `Done` — committed, tested, documented
- `Carried Over` — moved to the next sprint
- `Dropped` — abandoned; explain why in the report
- `Deferred` — intentionally postponed to a later sprint (add to
  "Deferred" section of `project_church.md`)

## Definition of Done

A backlog item is only `Done` when **all** of the following hold:

- [ ] Code committed to `main` (or merged from a feature branch)
- [ ] At least one test exercises the new behavior (unit or integration)
- [ ] If the item touches the domain model: Flyway migration is in place
      and idempotent
- [ ] If the item touches an API contract: `/shared/api/*` is updated
- [ ] If the item introduces a significant decision: an ADR is drafted or
      an existing ADR updated
- [ ] `project_church.md` capability list reflects the new behavior (at
      sprint end, not per item)
- [ ] Runs locally from a fresh `docker-compose up`

## Templates

See:

- [sprint-backlog-template.md](sprint-backlog-template.md)
- [sprint-report-template.md](sprint-report-template.md)
