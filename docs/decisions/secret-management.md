# Secret Management (I-03)

## Current state

Secrets reach the app exclusively through environment variables
(`JWT_SECRET`, `DB_PASSWORD`, `SPRING_DATA_REDIS_PASSWORD`,
`MFA_ENCRYPTION_KEY`, … — see `.env.example`). Production fails fast
without them:

- `jwt.secret: ${JWT_SECRET}` (no default) + `allow-default-secret: false`
  in `application-prod.yml`, enforced at boot by
  `ApplicationStartupValidator`;
- `app.security.mfa.encryption-key-base64: ${MFA_ENCRYPTION_KEY}` (Base64,
  32 bytes — TOTP-secret encryption at rest, see
  `docs/decisions/mfa-totp.md`), likewise enforced at boot in `prod`. Blank
  in dev/test means an ephemeral in-memory key (enrolled secrets die with
  the JVM, loudly logged) — never valid in production;
- CI (`ci.yml`, `mutation-nightly.yml`, `mutation-pr.yml`) prefers
  `secrets.JWT_SECRET` and otherwise mints a fresh ephemeral 256-bit key per
  run (`openssl rand -base64 32` into `GITHUB_ENV`) — no committed fallback
  exists anywhere in the workflows. Local `docker compose up` needs no secret
  at all: it boots with the dev default (equal to
  `JwtTokenProvider.DEFAULT_JWT_SECRET`, loopback-bound ports only), which
  production refuses twice at startup;
- `.gitleaks.toml` + the `secret-scan` CI job reject new hardcoded
  secrets; the allowlisted entries are the documented non-secret dev
  placeholders (the Base64 marker and its decoded
  `dev-only-NOT-FOR-PROD…` text) plus the two test-only password constants in
  `PasswordResetConcurrencyIT` / `MfaTransactionBoundaryIntegrationTest`
  (generic-api-key false positives). All are delimited so they cannot match
  inside a longer real key. `scripts/check_compose_jwt_default.py` fails CI if
  the marker drifts between the code constant, `application-dev.yml`,
  `docker-compose.yml` and the allowlist.

## Demo seed credentials (R-C06)

`DataSeeder` runs only under `(dev | demo) & !prod`. Its two demo users no
longer carry passwords in Java: they bind from `app.demo.seed.*`, which maps
to `DEMO_SEED_AHMET_PASSWORD` / `DEMO_SEED_AYSE_PASSWORD`. The `dev` profile
keeps the local-only defaults in `application-dev.yml` so a fresh checkout
still boots without an `.env`; the `demo` profile uses
`application-demo.yml`, where both placeholders have no default and startup
fails when they are unset. `DataSeeder` also fails fast on a blank bound value
instead of creating a user with an empty password. The dev defaults are
public by design and must never be reused outside a local database.

## What is still missing

Plain env-vars are not a secret store: they appear in CI logs on
misconfiguration, in shell history, and in every `.env` copy on a laptop.
For any deployment beyond staging:

## Migration path (External Secrets Operator, recommended)

1. Install ESO and a `ClusterSecretStore` backed by the cloud KMS/secret
   manager (Vault, AWS SM, GCP SM, Azure KV — any is fine).
2. Replace the Deployment env block with `envFrom.secretRef` / explicit
   `valueFrom.secretKeyRef` entries sourced from an `ExternalSecret`
   named e.g. `bank-app-secrets` (`JWT_SECRET`, `DB_PASSWORD`,
   `SPRING_DATA_REDIS_PASSWORD`). Never mount secrets as files unless
   the runtime needs them — env injection keeps the current
   `${…}` bindings in `application*.yml` untouched.
3. Keep `allow-default-secret: false` and `ApplicationStartupValidator`:
   they are the second layer if ESO ever serves an empty secret.
4. (Done) The former CI test-only fallback is removed: every CI environment
   either provides `secrets.JWT_SECRET` or mints an ephemeral key per run.

## Rotation runbook

1. Issue the new value in the secret manager (JWT: `openssl rand -base64 48`
   — the provider decodes Base64, 32+ bytes required).
2. Update the `ExternalSecret` / CI secret; rolling-restart the
   Deployment (`maxUnavailable: 0` keeps capacity).
3. JWT rotation invalidates outstanding access tokens (15 min TTL bounds
   the blast radius) and refresh tokens (clients re-login); DB password
   rotation briefly recycles the Hikari pool — watch
   `HikariPoolExhaustion` and `Http429RateHigh` during the rollout.
4. MFA-key rotation is DESTRUCTIVE (unlike JWT): AES-GCM cannot decrypt old
   secrets with a new key, so every enrolled user must re-enroll
   (enable → confirm). There is no bulk re-encryption path by design —
   announce the rotation, then verify `users` rows with `mfa_enabled = true`
   re-enroll before decommissioning the old key.
5. Revoke the old value at the manager; verify no pod still references it
   (`kubectl exec … env | grep -c` must be 0 for the old fingerprint —
   exact values never leave the manager).

## Token identity claims

JWT issuance and verification are pinned to `jwt.issuer` and
`jwt.audience` (defaults: `clean-architecture-bank` /
`clean-architecture-bank-api`). A token signed with the same HMAC key by a
sibling service is rejected unless it also carries both claims; issuer/audience
pinning is defense-in-depth, not a substitute for per-service keys. Every token also
publishes `kid` (`jwt.key-id`, default `primary`) in its header for future
multi-key rotation, and verification tolerates a bounded clock skew
(`jwt.clock-skew-seconds`, default 30, maximum 300). The skew also extends the
blacklist TTL so a revoked token cannot reappear during the grace period.

**Rollout note:** tokens minted before this change have no `iss`/`aud` and are
rejected. Access tokens would age out within 15 minutes, but refresh tokens
are rejected immediately, so deploy with a release note that users must sign
in again. `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_KEY_ID` and
`JWT_CLOCK_SKEW_SECONDS` override the defaults without a rebuild.

## 2026-10 dev-default rotation (O-1)

The former DEFAULT_JWT_SECRET value appeared in git history and therefore counts as compromised. It was replaced with a deliberately non-secret, zero-entropy marker: the Base64 value `ZGV2LW9ubHktTk9ULUZPUi1QUk9ELTAwMDAwMDAwMDA=` (decodes to `dev-only-NOT-FOR-PROD-0000000000`, exactly 32 bytes, R-O07). The same literal is used in `JwtTokenProvider.DEFAULT_JWT_SECRET`, `application-dev.yml` and `docker-compose.yml`; `scripts/check_compose_jwt_default.py` fails CI if the three drift or if the value stops being a documented `NOT-FOR-PROD` marker, and `.gitleaks.toml` allowlists both forms. Prod behavior is unchanged: the marker is refused twice (`allow-default-secret: false` + `ApplicationStartupValidator`).

History still contains the old value: treat any pre-rotation token signed with it as compromised (short TTL bounds the exposure) and, for full hygiene, rewrite history with git filter-repo or rotate the repository if the history was ever pushed to an untrusted remote. New hardcoded secrets remain rejected by gitleaks + the secret-scan CI job.
