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
