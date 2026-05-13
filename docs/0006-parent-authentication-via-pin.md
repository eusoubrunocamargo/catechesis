# ADR-0006 — Parent Authentication via Per-Child PIN

**Status:** Accepted
**Date:** 2026-05-16
**Supersedes:** the "device binding" mechanism proposed in `docs/roadmap.md` (Sprint 3 — Events & Snack Lists)
**Extends:** ADR-0003 (redaction scope addendum)

## 1. Context

The original problem — what parents do today on WhatsApp — sets the friction baseline. A parent receives the snack list copy-pasted into a group chat, edits her line, and re-sends the whole message. Two parents editing at once silently overwrite each other. The pain is real, but the *interaction* is light: read a message, write a message, hit send. Whatever the system asks parents to do has to feel lighter than that, not heavier — every additional tap costs adoption.

This rules out account creation, password recall, and email verification flows. It also rules out anything that requires a parent to remember where they registered or which device they used last. The system needs a way to attribute a snack claim to a specific child — and therefore to the parent who registered that child — with the smallest possible interaction.

The roadmap's original answer was *device binding*: when a parent first opens the event link, the server issues a cookie that ties this device to a specific child for future visits. Subsequent claims are attributed by reading the cookie. During Sprint 3 planning, this design was interrogated and the underlying goal restated: *friction reduction*, not any particular security property. Once that was clear, simpler mechanisms became viable — and a 6-digit PIN, chosen once at registration, turned out to deliver the same friction reduction without the device-side state that makes binding fragile in practice.

## 2. Decision

- **Scope:** one PIN per child. Siblings get independent PINs. There is no parent entity.
- **Shape:** 6 numeric digits, parent-chosen at registration submission, validated server-side with `@Pattern(regexp = "\\d{6}")`.
- **Storage:** a `parent_pin_hash` column on both `pending_registration` and `child`. The hash is copied from the pending row to the new Child row at approval time.
- **Algorithm:** SHA-256 of `pepper || pin`, hex-encoded. The column is `VARCHAR(64)`. The pepper is a deployment-wide secret, loaded from `app.pin.pepper`, sourced from an environment variable in production and from `.env.dev` in development. The application MUST refuse to start if the pepper is missing or empty.
- **Uniqueness:** `UNIQUE (klass_id, parent_pin_hash)` on both `pending_registration` and `child`. A second parent attempting to register a child with a PIN already in use within the same class receives 409 Conflict at submission time.
- **Reset:** none in MVP. A future ADR will address the reset workflow.

## 3. Rationale

### 3.1 Why PIN, not device binding

Device binding sounds elegant on paper but breaks in three routine cases:

1. **Cleared cookies / private browsing.** The parent re-opens the event link from a "private" tab and is treated as unknown. They have no recourse.
2. **Second device.** Mom's phone died; dad is now using his own phone, which has never seen this event link before. Same outcome: unknown.
3. **Shared device.** One phone, two children in different classes. Whose binding does the cookie hold?

A PIN survives all three. The parent types the same six digits whether the device is new, old, private, or shared. The model also eliminates an entire category of bugs that binding introduces — "what if the binding is wrong?" — because there is no binding to be wrong.

### 3.2 Why per-child, not per-parent

A parent entity buys sibling consolidation: one PIN, two children, one tap. It charges a parent-identity model: phone or email as the identifier, dedup logic when two registrations claim the same identity, a path for "we have a new phone number," and a decision about what to do when separated parents register the same child twice.

At the scale the system is built for — a single parish, ~30 children per class, a handful of classes — the consolidation savings are small. A parent with two children in the same class taps a 6-digit PIN twice instead of once per event. That cost is concrete and bounded. The identity-model costs are unbounded and recur every time a parent's contact information changes. The simpler model wins.

### 3.3 Why SHA-256-with-pepper, not BCrypt

This is the load-bearing decision in this ADR, so it gets the longest treatment.

BCrypt is the right hash for *passwords*. Three properties make it so: the value space is enormous (passwords have ≥40 bits of entropy in practice), online-guessing throughput is the active threat being slowed down, and verification happens against a *known* user's stored hash (the username narrows the lookup to a single row).

PINs invert all three:

- **Value space is small.** A 6-digit PIN has ~20 bits of entropy (1M values). No amount of per-hash slowness saves a value space this small from offline brute-force *if the attacker has the hashes*. The defense against brute force has to live elsewhere.
- **The active threat is not online speed.** The per-IP rate limit on `/public/**` (from S02-07, ~10 requests/hour) bounds online attempts to roughly 88,000 PIN guesses per year per IP — and the PIN space is 1M, so a single IP needs over a decade to expect a hit against one child. Multiple IPs help, but distributed attacks are out of scope for MVP. Slowness in the hash function would buy nothing on top of the rate limiter.
- **Lookup is by hash, not by user.** The endpoint `POST /public/events/.../identify` knows only `(classSlug, eventSlug, pin)`. There is no username to narrow the search. The query is "find the Child in this class whose hash matches" — and BCrypt's per-hash salt makes that query impossible to index. The database must scan every Child in the class, decode each row's stored BCrypt hash, and run `matches(plaintext, stored)` against each. At ~100ms per BCrypt comparison and ~30 children per class, that is ~3 seconds of CPU per identification attempt, on a mobile parent flow. Unacceptable for the friction baseline established in §1.

SHA-256-with-pepper inverts all of these:

- **Deterministic.** `SHA-256(pepper || pin)` produces the same output for the same input. The column is indexable; lookup is O(log n) on a B-tree index.
- **Fast.** Hashing is microseconds, not 100ms. The mobile flow stays fluid.
- **Pepper defends against the realistic offline threat.** An attacker with read-only DB access — the realistic compromise scenario for a small parish system — has the hashes but not the pepper (which lives in deployment config, never in the DB). Without the pepper, they cannot compute `SHA-256(pepper || pin)` for any candidate PIN, so they cannot rainbow-table the column. They would first have to compromise the deployment environment to recover the pepper, at which point they have far more direct attacks available.
- **The rate limiter is the load-bearing online defense.** This is not a workaround; it is the right defense for the threat model. The hash's job is narrower than BCrypt's: not "slow down online attackers" (the rate limiter does that), but "prevent plaintext recovery from a leaked database" (the pepper does that).

## 4. Alternatives Considered and Rejected

- **Device binding.** See §3.1. Rejected because device-side state is fragile in routine cases and the cookie-lifecycle code introduces an entire class of bugs that the PIN model does not have.
- **Parent entity with shared PIN.** See §3.2. Rejected because the model complexity (identity, dedup, contact-change paths) exceeds the bounded UX cost of per-child PINs at the system's scale.
- **CPF or phone-based identity.** Rejected on two grounds. First, both are Tier-3 PII whose collection would force LGPD justification for a purpose (authentication) that does not require persistent identity. Second, both create real friction: CPF feels heavyweight for "I'm bringing snacks Sunday," and phone numbers are mistyped in ways the system has no way to recover from without a verification flow (SMS), which puts the friction floor higher than WhatsApp's.
- **BCrypt for the lookup column.** See §3.3. Rejected because BCrypt's non-deterministic output makes the column non-indexable, forcing a full-class scan with ~100ms per row. The right hash for this lookup is deterministic and fast; the security properties BCrypt is designed for do not match the threat model.

## 5. Consequences

- **Schema.** Flyway V10 adds `parent_pin_hash VARCHAR(64) NOT NULL` to both `pending_registration` and `child`, with a `UNIQUE (klass_id, parent_pin_hash)` constraint on each table and a supporting index `(klass_id, parent_pin_hash)`. Sprint 3 work item S03-02.
- **Configuration.** A new property `app.pin.pepper` is required. Sourced from an environment variable in production, from `.env.dev` in development, and never committed to the repository. The application performs a startup check that refuses to boot if the property is missing, empty, or whitespace-only.
- **No pepper rotation in MVP.** Changing the pepper after the first parent registers invalidates every existing PIN — the hashes in the database become unverifiable against any submitted PIN. This is acceptable for MVP because no production deployment exists yet; a future ADR will address rotation if it becomes necessary.
- **Redaction scope.** ADR-0003's LGPD redaction scope is extended: `parent_pin_hash` is *not* part of the redaction set on either `child` or `pending_registration`. It is the access credential, not Tier-2/3 data. `RosterRedactionService` must explicitly preserve it; `RosterRedactionServiceIntegrationTests` gains an assertion that the hash survives redaction.
- **Reset gap.** No HTTP endpoint exists for PIN reset in MVP. A future ADR will address the in-app reset workflow.
- **Raw-PIN invariant.** The raw PIN value MUST NEVER appear in logs, error messages, server-side debug output, response bodies, or any catechist-visible surface. The only place the raw value exists is in the parent's submitting browser; everything downstream of the request handler sees only the hash. Code review enforces this; any future logging or request-body-capture code must filter the `parentPin` field.
- **Test obligation.** S03-17's quality gate gains a property: a wrong PIN returns 401 with no information distinguishing "PIN does not exist for this class" from "PIN exists for another class" — the same response shape in both cases.
- **Auth seam.** ADR-0004's pluggable security seam (dev filter today, Google OAuth in Sprint 5) is *not* the path PIN authentication takes. The seam is for catechist and SuperAdmin identity, which is durable and account-based; PIN authentication is per-request, anonymous-presenting, and resolved entirely within the public-surface controllers. This is by design — wiring PIN auth through the seam would force a false symmetry between two very different identity models.

## 6. References

- [ADR-0003](0003-security-posture-and-data-sensitivity.md) — Security Posture and Data Sensitivity. This ADR extends ADR-0003's redaction scope.
- [ADR-0004](0004-deferred-authentication-strategy.md) — Deferred Authentication via Security Seam. PIN authentication is the first non-Lead authenticated surface but does not flow through this seam.
- Sprint 3 backlog items: S03-02 (Flyway V10), S03-03 (registration field + approval copy), S03-13 (`POST /public/events/.../identify`), S03-17 (quality gate).
- `docs/sprints/sprint-03-backlog.md` — full Sprint 3 plan.