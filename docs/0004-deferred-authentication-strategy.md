# ADR-0004: Deferred Authentication via a Pluggable Security Seam

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Bruno (solo)

## Context

Authentication will be implemented later in the project lifecycle. Early
development happens on a corporate network with restricted outbound
access — Google OAuth endpoints and similar external identity providers
may be intermittently reachable. Blocking feature development on a
finalized auth solution would stall the project for no MVP-relevant
benefit.

However, the data model and access patterns must be **designed as if auth
exists from day one**. Retrofitting auth into code that assumes
"everybody can do everything" is expensive and bug-prone. Every service
must already have a notion of "who is the current catechist?" even when
that answer comes from a dev stub.

Decided in ADR-0005 and earlier discussion:
- Production auth will be **Google OAuth 2.0** via Spring Security
  OAuth2 Client
- Catechist accounts are **pre-provisioned** (whitelist of emails) by
  the Super-Admin or by a Lead Catechist inviting into their church
- Parents **never log in**; the device-cookie binding covers their identity
- Sessions are **stateful** (server-side, Postgres-backed via Spring
  Session), 30-day sliding expiry

## Decision

Implement a **pluggable security seam** with two interchangeable
implementations:

### The seam

- A `SecurityContext` abstraction, populated at request entry by a servlet
  filter, exposing: `currentCatechistId`, `currentChurchId`, `role`,
  `isAuthenticated()`, `isSuperAdmin()`.
- All downstream code (services, repositories) reads from this context.
  No controller reads request headers or tokens directly.

### Implementation A — Dev Mode

- Active when `app.auth.mode=dev`
- A `DevAuthenticationFilter` reads `X-Dev-Catechist-Id` (or
  `X-Dev-Super-Admin-Id`) from the request header
- Looks up the catechist/super-admin in the database and populates
  `SecurityContext`
- If the header is missing on a protected endpoint, returns 401
- A visible banner on the frontend ("DEV MODE — AUTH DISABLED") removes
  any doubt about the environment

### Implementation B — Production

- Active when `app.auth.mode=google`
- Standard Spring Security OAuth2 Client flow
- On successful Google callback, `OAuth2UserService` looks up the
  verified email in the `catechist` and `super_admin` tables
- If found, populates `SecurityContext`; if not, rejects with a clear
  message
- Server-side session in Postgres, HTTP-only cookie

### Safety rails

- A `@Profile("prod")` startup check refuses to boot if
  `app.auth.mode=dev` is set in production
- Integration tests run in dev mode but assert that `SecurityContext`
  is populated for every non-public endpoint
- A small set of contract tests run against **both** modes, proving the
  seam is genuinely swappable

## Consequences

### Positive

- Feature work unblocked; auth is just one more swap-in later
- Code written today naturally follows the authz pattern (reads from
  context, doesn't skip checks)
- Easy to simulate any user in dev (flip a header, reload)
- Future auth providers (email magic link, other OAuth providers) are
  additional implementations of the same seam
- CI and local development never touch Google's endpoints

### Negative

- A `X-Dev-Catechist-Id`-based filter is a foot-gun if it ever ships to
  production (mitigated by the startup check)
- Two code paths to maintain; risk of drift if only one is tested
  (mitigated by the cross-mode contract tests)
- Developers must discipline themselves to populate the header on every
  Postman request; helper scripts should provide sensible defaults

### Follow-ups

- Provide a `scripts/dev-login.sh` that prints ready-to-use curl
  headers for a few seeded catechist personas
- When Google OAuth is wired up (a dedicated sprint), add a third
  implementation `TestAuthenticationFilter` for integration tests that
  bypasses OAuth entirely and authenticates by direct DB lookup
- Revisit this ADR when introducing magic link auth or a second OAuth
  provider
