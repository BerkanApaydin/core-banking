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

- Merging is per field: a canonical field set to a non-default value wins,
  otherwise the legacy field applies. A canonical field left exactly at its
  default is indistinguishable from unset.
- Set every key under ONE prefix per deployment; prefer the canonical one for
  new configuration.
- `application.yml` maps both prefixes to the same env placeholders
  (`CACHE_ACCOUNT_INFO_*`), so they agree unless explicitly overridden.
  `application-prod.yml` pins both to the literal `redis` (asserted by
  `ProductionConfigContractTest`); canonical wins on conflict.
