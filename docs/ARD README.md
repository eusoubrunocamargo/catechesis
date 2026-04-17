# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for the
Organized Catechesis project. Each ADR captures one significant decision,
its context, and its consequences — so that future contributors (and future
sessions) can understand *why* the system is built the way it is.

## Format

Every ADR follows the same three-section structure:

- **Context** — the forces at play, the problem being addressed
- **Decision** — what we decided, stated clearly and unambiguously
- **Consequences** — what follows from the decision, good and bad

Each ADR has a status: `Proposed`, `Accepted`, `Superseded by ADR-XXX`, or
`Deprecated`. New ADRs are numbered sequentially and never renumbered.
When a decision changes, write a new ADR that supersedes the old one —
do not edit history.

## Index

| ID | Title | Status |
|----|-------|--------|
| [ADR-0001](0001-monorepo-and-modular-monolith.md) | Monorepo and Modular Monolith Architecture | Accepted |
| [ADR-0002](0002-multi-tenancy-model.md) | Multi-Tenancy Model: Shared Database, Logical Isolation | Accepted |
| [ADR-0003](0003-security-posture-and-data-sensitivity.md) | Security Posture and Data Sensitivity Classification | Accepted |
| [ADR-0004](0004-deferred-authentication-strategy.md) | Deferred Authentication via a Pluggable Security Seam | Accepted |
| [ADR-0005](0005-operational-super-admin-role.md) | Operational Super-Admin Role | Accepted |

## How to propose a new ADR

1. Copy an existing ADR as a template
2. Number it sequentially (next free number, zero-padded to 4 digits)
3. Title it `NNNN-short-descriptive-kebab-case.md`
4. Open as `Proposed`, flip to `Accepted` once the team (or solo dev) commits to it
5. Add the row to the index above
