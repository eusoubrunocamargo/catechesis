# ADR-0003: Security Posture and Data Sensitivity Classification

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Bruno (solo)

## Context

The system stores personal data about minors — names, allergies, emergency
contact details — under Brazilian LGPD (Lei Geral de Proteção de Dados).
The operator of the system is a volunteer-run parish, not a professional
IT organization. The threat model is unusual:

- The biggest threat is not sophisticated attackers; it is **accidental
  exposure** (a shared link forwarded to the wrong group, a mis-scoped
  query returning another class's children, a backup leaking)
- Secondary threat: **well-intentioned misuse** (a curious parent probing
  URLs, a scraped event page)
- Genuine malicious attacks are unlikely but not impossible

The system must adopt a **privacy-by-default** posture while remaining
buildable by one person on a proxy-restricted network.

## Decision

Data is classified into three sensitivity tiers, each with its own rules:

### Tier 1 — Public within the trust boundary

Catechist names, class names, event dates, snack categories and slot
counts. Visible via the event link to anyone in the WhatsApp group.
No encryption requirements. Normal access controls.

### Tier 2 — Tenant-scoped personal data

Child's first name, child's last name, parent-provided notes, snack
claim history. Visible to catechists of the owning class and to parents
in the context of a single event. Strictly scoped by `church_id` and
`class_id`. No encryption at the column level; relies on DB-level at-rest
encryption and strict query scoping.

### Tier 3 — Sensitive personal data

Allergies, emergency contacts. Lives in a separate table
(`child_safety_info`) readable only by assigned catechists of the owning
class. **Never** exposed on parent-facing URLs. Accessed only through
a narrow service method that writes an `AuditEvent` on every read.

### System-wide controls

- **TLS everywhere**, from day one, including local development (mkcert
  or equivalent). No plaintext endpoints exist.
- **Encryption at rest** via managed Postgres (or disk-level if
  self-hosted). Backups inherit this.
- **HTTP security headers**: HSTS, `X-Content-Type-Options`,
  `Referrer-Policy: strict-origin-when-cross-origin`,
  `Content-Security-Policy` appropriate for the frontend.
- **Cookies**: `HttpOnly` + `Secure` + `SameSite=Lax` for session cookies,
  `SameSite=Strict` for admin cookies once auth exists.
- **No public read on storage buckets.** Any file (photos, attached
  documents) served via short-lived signed URLs generated per request.
- **No PII in URLs.** Ever. Queries go in request bodies or
  authenticated endpoints.
- **No PII in logs.** Structured logging uses IDs only; resolve to names
  in the UI, not the log stream.

### LGPD alignment

- Consent captured at registration: timestamp + consent-text version
  stored on `PendingRegistration`
- Purpose clearly stated: "dados utilizados exclusivamente para
  organização da catequese e segurança da criança"
- Retention: end of academic year (administrative action, not automatic)
- Right of erasure: redaction of sensitive fields in both active and
  historical records; audit skeleton preserved
- Controller of record: the parish (not the developer); reflected in UI
  copy

### Data minimization

- **Photos of children are out of scope for MVP.** Not stored, not
  uploaded, not supported.
- Emergency contacts: name + phone only. No address, no ID number, no
  relationship-to-child free text beyond an optional 20-char label.
- Allergies: free text, bounded length (500 chars). UI copy explicitly
  requests "only safety-relevant information."
- No analytics, no tracking pixels, no third-party scripts on any page
  that renders child data.

## Consequences

### Positive

- Clear mental model for where sensitive data lives and who can read it
- Easy to reason about "what does a breach of X look like?" per tier
- LGPD posture defensible without lawyer involvement at MVP stage
- Minimization keeps the attack surface small

### Negative

- Tier-3 queries require going through the audit-writing service method,
  not direct repository access (slight API ceremony)
- Photos being out of scope may be reintroduced later with migration cost
- Redaction flow adds complexity to the PendingRegistration lifecycle

### Follow-ups

- Every controller method that returns data must declare its tier in a
  code comment or annotation, so reviews can spot tier-3 leaks
- Integration tests must assert: anonymous requests cannot read
  `child_safety_info` fields under any URL
- A quarterly manual review: re-read this ADR, audit a random event link,
  verify no Tier-3 data is exposed
- A proper Data Processing Agreement template for parishes to sign when
  onboarding, deferred to first real deployment

  ---

## Addendum: LGPD Redaction Scope (added Sprint 2, S02-12)

**Date:** 2026-05-15

When implementing the redaction service (`RosterRedactionService`) for
LGPD right-of-erasure in Sprint 2, the operational question arose:
*which fields does erasure clear, and which are preserved for audit?*

### Decision

Erasure clears Tier-3 sensitive fields. Identity fields and audit
metadata are preserved.

**Cleared on redaction:**

- `pending_registration.allergies`
- `pending_registration.emergency_contacts`
- `pending_registration.parent_contact_email`
- `child_safety_info.allergies`
- `child_safety_info.emergency_contacts`
- `child_safety_info.notes`
- A `sensitive_data_redacted_at` timestamp is set on both rows

**Preserved on redaction:**

- All primary identifiers (`id`, `church_id`, `klass_id`)
- The child's first and last name (on both `child` and the originating
  `pending_registration`)
- Registration status (`PENDING`, `APPROVED`, `REJECTED`) and review
  metadata (`reviewed_by_catechist_id`, `reviewed_at`)
- Consent metadata (`consent_version`, `consent_granted_at`)
- All timestamps (`submitted_at`, `created_at`, `updated_at`)

### Rationale

LGPD treats personal data broadly — a literal interpretation would
suggest erasing the child's name as well. The standard interpretation
for systems with legitimate retention obligations (which a parish has,
for child-safety records) is: keep the minimum identifying information
needed for the audit to function, and erase everything else.

Without the child's name preserved, the audit ledger cannot identify
*whom* the audit is about. The audit's value collapses. Preserving the
name, while clearing allergies, contacts, and parental email, gives a
balance: an auditor can verify "this child was admitted via this consent
at this time" without retaining the sensitive ancillary content the
parent originally submitted.

### Scope and reversibility

This decision applies to the redaction service in its current form. If
a parish or legal review at any point requires stricter erasure — for
example, complete erasure of the child's name — a separate
`redactIdentity()` lifecycle operation can be introduced as a distinct
gesture. The current redaction set is the floor; nothing prevents a
stricter operation from layering on top.

### What this does NOT cover

- **Server logs.** This addendum addresses persistent storage. Audit
  logging (Sprint 4) will need its own redaction policy — likely
  excluding sensitive fields from log output entirely, rather than
  redacting them later.
- **Backups.** Database backups will contain pre-redaction state.
  Sprint 4 deployment hardening should establish a backup-redaction
  policy (likely: time-bounded retention with rolling deletion).
- **Frontend caches.** Once a frontend exists, browser-side state
  needs its own erasure consideration. Out of scope for MVP backend.
