# Secret Management (I-03)

## Current state

Secrets reach the app exclusively through environment variables
(`JWT_SECRET`, `DB_PASSWORD`, `SPRING_DATA_REDIS_PASSWORD`, … — see
`.env.example`). Production fails fast without them:

- `jwt.secret: ${JWT_SECRET}` (no default) + `allow-default-secret: false`
  in `application-prod.yml`, enforced at boot by
  `ApplicationStartupValidator`;
- CI (`ci.yml`, `mutation-nightly.yml`, `mutation-pr.yml`) injects
  `secrets.JWT_SECRET` with a documented **test-only** fallback that can
  never authenticate in prod (last character differs from the public dev
  default on purpose);
- `.gitleaks.toml` + the `secret-scan` CI job reject new hardcoded
  secrets; the two allowlisted values are the test-only fallbacks above.

## What is still missing

Plain env-vars (and especially committed fallbacks, even test-only ones)
are not a secret store: they appear in CI logs on misconfiguration, in
shell history, and in every `.env` copy on a laptop. For any deployment
beyond staging:

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
4. Remove the CI fallback only after `secrets.JWT_SECRET` exists in
   every environment that runs CI — until then the fallback keeps forks
   green without weakening prod (it cannot satisfy the prod guard).

## Rotation runbook

1. Issue the new value in the secret manager (JWT: `openssl rand -base64 48`
   — the provider decodes Base64, 32+ bytes required).
2. Update the `ExternalSecret` / CI secret; rolling-restart the
   Deployment (`maxUnavailable: 0` keeps capacity).
3. JWT rotation invalidates outstanding access tokens (15 min TTL bounds
   the blast radius) and refresh tokens (clients re-login); DB password
   rotation briefly recycles the Hikari pool — watch
   `HikariPoolExhaustion` and `Http429RateHigh` during the rollout.
4. Revoke the old value at the manager; verify no pod still references it
   (`kubectl exec … env | grep -c` must be 0 for the old fingerprint —
   exact values never leave the manager).
