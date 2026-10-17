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
- **Enrollment is two-step** (enable → confirm): MFA switches on only after a
  code proves possession, so an abandoned enrollment can never lock a user out.
  Disabling requires password re-authentication + a current code.
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

## Alternatives considered

- **SMS/email OTP codes instead of TOTP**: no extra app for users, but needs
  a real provider on day one (out of scope) and is phishable/SIM-swappable;
  TOTP works fully offline with the simulation notifier.
- **WebAuthn/passkeys**: stronger, but needs frontend ceremony + credential
  storage; recorded as the next step up from TOTP, not a replacement for this
  change.
- **Mandatory MFA for all users**: rejected — enrollment friction for existing
  users; revisit with an admin enforcement flag once adoption is measured.
