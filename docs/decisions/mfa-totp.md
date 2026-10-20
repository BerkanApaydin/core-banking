# MFA via TOTP + Password Reset (S11/D23)

## Status

Accepted — implemented (V54/V55, user BC).

## Context

The audit found the two largest authentication gaps: no second factor (A07)
and no password-reset flow. User-enumeration, brute-force and token hygiene
guards already existed around login; MFA had to compose with them, not bypass
them.

## Decision

- **TOTP (RFC 6238, HMAC-SHA1), zero new dependencies**: `Totp` is pure
  JSE in `user.domain`, verified against the RFC 6238 Appendix B vectors.
  Step size, code length and skew are configurable via `MfaProperties`
  (`app.security.mfa`: defaults 30s / 6 digits / ±1 window; digits 6–8,
  skew 0–2 enforced by both `MfaProperties` and `TotpPolicy`). The
  enrollment `otpauth://` URI is built from the same policy values
  (`EnableMfaUseCaseImpl.withPolicy`), so a non-default configuration
  cannot desync the authenticator app from server verification.
  Per-user opt-in (`users.mfa_enabled`); login carries an optional
  `totpCode` that is REQUIRED when the flag is on.
- **Secrets encrypted at rest (AES-256-GCM)** in
  `users.mfa_secret_encrypted`; the key comes from `MFA_ENCRYPTION_KEY`.
  Plaintext secrets never touch the DB, the domain, or logs. Blank key =
  ephemeral in-memory key (dev/test only) and a **fail-fast prod startup
  error**.
- **Encryption-key rotation (2026-10)**: ciphertexts are
  `v1:<keyId>:<Base64(iv || ct)>`, where `keyId` is derived from the key
  (SHA-256 prefix) and never contains secret material. Old rows written
  before key ids (plain `Base64(iv || ct)`) still decrypt. During rotation,
  set the new key in `MFA_ENCRYPTION_KEY` and keep the old one(s) in
  `MFA_ENCRYPTION_PREVIOUS_KEYS` (comma-separated, 32-byte Base64): decryption
  tries the primary key then the previous keys, and the first successful
  login/confirm re-encrypts the row with the primary key. Remove a previous
  key only after no rows remain under it. The startup validator rejects
  malformed/short keys in the ring, and a duplicate key id fails the bean.
- **Enrollment is two-step and step-up protected** (enable → confirm): both
  steps require the account password, and MFA switches on only after a code
  proves possession, so an abandoned enrollment can never lock a user out.
  Disabling requires password re-authentication + a current code; confirm and
  disable also revoke every refresh-token family.
- **Login failures are uniform**: missing/malformed/mismatched codes produce
  the exact same 401, brute-force record and audit row as a bad password (no
  oracle on enrollment, secret validity, or failure kind). The second factor
  rides the authenticated identity (no user reload — the ArchUnit ban on a
  second load in login still holds).
- **Authenticated MFA endpoints fail with 400, not 401**: confirm/disable
  run inside a valid session, and the UI treats 401 as session expiry (it
  would log the user out on a mere typo). Wrong codes/passwords there share
  one fixed `MFA_CODE_INVALID` message, so there is still no oracle on which
  factor failed. Password-reset confirm (public) is 400 for the same reason.
- **Password reset is enumeration-safe**: request always returns 202 with the
  same audit shape; confirm uses one generic message for
  unknown/expired/used tokens; consumption is a conditional UPDATE (C-1
  pattern) so concurrent confirms cannot double-apply; the password change
  bumps `tokenVersion`, retiring stolen sessions.
- **Delivery is simulated**: `LoggingPasswordResetNotifier` logs only a
  SHA-256 digest prefix for correlation (never the raw token — raw tokens
  are password-equivalent and must never reach logs), like the transfer
  simulation adapters. A production deployment MUST replace this bean
  with a real email/SMS provider.
- **Second-factor failure mapping**: a corrupt secret (wrong rotation key,
  tampered row) fails closed as `MFA_CODE_INVALID` (400) on the
  authenticated confirm/disable endpoints — never a 500. On the login path
  the same state fails as the uniform 401, except a secret-*store outage*,
  which propagates as 503 without touching the brute-force counter (an
  infra cut must not lock users out).
- **Token-consumption race closed**: the single-use conditional UPDATE
  additionally requires `expiresAt > usedAt`, so a token expiring between
  the SELECT and the UPDATE cannot be consumed. Unknown reset identifiers
  perform dummy mint+digest work so the timing gap does not oracle account
  existence.
- **Public-path cookie guard**: password-reset endpoints are `permitAll`,
  and the JWT filter skips the browser session cookie on them (same as
  login/register) — a stale cookie on a shared browser must not turn a
  public reset request into a 401. Enrollment material (`mfa/enable`) is
  served `Cache-Control: no-store`; prod startup additionally rejects a
  non-Base64 or non-32-byte `MFA_ENCRYPTION_KEY` with an explicit message.
- **MFA endpoints are rate-limited (2026-10)**: `/api/v1/auth/mfa/*` was
  missing from `app.security.rate-limit.paths`, so the 6-digit code check
  had no budget at all. It now inherits the tight auth tier (10 requests /
  10s, IP-keyed) because it lives under the `/api/v1/auth` prefix.
- **Per-account MFA attempt budget (2026-10)**: the IP-keyed budget cannot
  contain a distributed guess, and an IP-keyed account lockout would let one
  abusive client lock a victim out of their own second factor. A separate
  ACCOUNT-keyed counter (`MfaAttemptPort`, `app.security.mfa.max-attempts`
  / `window-minutes`, backed by the same Redis store as the login guard in
  production) locks the account out after 5 failed codes for 15 minutes
  and answers 429 (`TOO_MANY_FAILED_MFA_ATTEMPTS`). Every code check
  consults `isBlocked` first: the management endpoints per request, and the
  login endpoint after the password check (so the lockout cannot be
  sidestepped through login, and an unauthenticated caller learns nothing).
- **TOTP replay protection at login (2026-10, V56)**: a code is valid for
  `2 * skew + 1` steps (90s with the default skew), so a captured code
  could be replayed. `users.mfa_last_totp_counter` stores the step counter
  of the last consumed code; the write is a CONDITIONAL update (only a
  strictly newer counter is accepted), so the database — not the
  application — arbitrates a replay and two concurrent replays cannot both
  win. The counter is consumed by the LOGIN path only
  (`MfaCodeVerificationService.verifyAndConsume`): the enrollment
  confirmation and the MFA removal do not mint a session, and consuming the
  counter there would reject the very next legitimate use of the same code
  (confirm, then log in, inside one 30-second step). Those two paths use
  `verify` (no consumption). The removal path accepts one residual window: a
  captured, not-yet-consumed code stays usable for removal inside its
  validity window. That is bounded by the removal flow itself requiring the
  account password and an authenticated, CSRF-protected session; consuming
  instead would reject a legitimate "log in, then disable MFA" sequence
  inside one step.
- **All three code checks share one service** (`MfaCodeVerificationService`):
  the brute-force bound, the replay protection and the uniform failure
  contract live in one place instead of being re-implemented per use case.
  A secret-store outage still propagates as 503 and is NOT counted as a
  failed attempt.

## Consequences

- New public (permitAll) paths: `/api/v1/auth/password/reset/request` and
  `/confirm` — allow-listed in `PublicApiPaths`, both whitelists, and the
  auth-tier rate budget (6.3 drift test keeps prod in step).
- New native `AuditAction` values (`MFA_ENABLED`, `MFA_DISABLED`,
  `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`) + V55 enum values.
- `AuthenticatedUser`/`CustomUserDetails` carry `mfaEnabled` (default false —
  existing constructors delegate, no flag-day).
- Reset tokens: one live per user, 15-minute default lifetime, SHA-256 digests
  at rest, `ON DELETE CASCADE` with the user.
- New column `users.mfa_last_totp_counter` (V56) — the replay counter.
- New port `MfaAttemptPort` (user BC) with a Caffeine backend (dev/test) and
  a Redis backend (production); the store follows
  `app.security.failed-login.backend` so the MFA budget is shared across
  replicas in production and per-JVM only in dev/test.

## Alternatives considered

- **SMS/email OTP codes instead of TOTP**: no extra app for users, but needs
  a real provider on day one (out of scope) and is phishable/SIM-swappable;
  TOTP works fully offline with the simulation notifier.
- **WebAuthn/passkeys**: stronger, but needs frontend ceremony + credential
  storage; recorded as the next step up from TOTP, not a replacement for this
  change.
- **Mandatory MFA for all users**: rejected — enrollment friction for existing
  users; revisit with an admin enforcement flag once adoption is measured.
