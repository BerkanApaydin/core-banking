# Snapshot-cache key alias: canonical prefix

**Decision (2026-10):** the canonical snapshot-cache keys are
`app.cache.account-info.*`. The historical `app.cache.caffeine.account-info.*`
keys stay bound as a deprecated fallback.

**Why:** the `caffeine` infix misleads operators into reading the whole block
as single-JVM configuration, while the value selects the backend
(`caffeine` = single-JVM, `redis` = shared). The canonical prefix names what
the block configures (the account-info snapshot cache), not one of its
backends.

**Semantics** (single source of truth: `CacheBackendResolution.resolve`,
used by the `@Conditional` backend selection, the resolved settings bean and
`ApplicationStartupValidator`):

- Merging is per field and decided by PRESENCE, not by value: when the
  canonical key is set — even to its default value — the canonical value
  wins; otherwise the legacy value applies. (A value comparison would make
  "set to the default" indistinguishable from "unset" and hand the field to
  the legacy prefix — the exact inversion the canonical/legacy split is
  meant to prevent. Changed 2026-10.)
- Set every key under ONE prefix per deployment; prefer the canonical one for
  new configuration.
- `application.yml` keeps the `CACHE_ACCOUNT_INFO_*` placeholders under the
  legacy prefix ONLY and leaves the canonical keys unset: presence semantics
  require "unset" to be reachable. An explicitly set canonical key always
  wins (even at its default value); a legacy-only deployment — and a typo in
  its backend value, which the startup validator rejects — still resolves
  through the alias. `application-prod.yml` pins the canonical key to the
  literal `redis` and mirrors it on the alias (both asserted by
  `ProductionConfigContractTest`); canonical wins on conflict. Do not define
  the canonical keys in the base file: every deployment would become
  "canonical" and the fallback would silently die (2026-10).
