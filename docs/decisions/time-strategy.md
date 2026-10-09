# Time Strategy: UTC-Only Timestamps (Kademe 1)

**Decision:** Every `LocalDateTime` in this codebase means UTC. No exceptions.

**Why this instead of a full `Instant` migration:** `LocalDateTime` already flows
through domain, ports, entities, mappers, DTOs, API params and 45 migrations.
Migrating all of it to `Instant` buys nothing once a single zone is enforced —
a zone-less type is harmless when its only possible reading is UTC. What broke
us twice was never the type: it was two clocks (UTC production clock vs
`systemDefaultZone()`/bare `now()`) feeding one timeline, 3 hours apart on
developer machines (Istanbul) while CI/prod agreed on UTC.

**Rules (enforced, not advised):**
1. Production code never calls `LocalDateTime.now()`, `Clock.systemDefaultZone()`
   or `ZoneId.systemDefault()` — pinned by `CodingRulesArchitectureTest`
   (`noBareNowInMainCode`, `noSystemDefaultZoneInMainCode`). All "now"s come
   from the injected `ClockProviderPort` (UTC) or an explicit UTC clock.
2. Tests use UTC clocks (`Clock.systemUTC()` or fixed UTC instants). Bare
   `LocalDateTime.now()` fixtures stay allowed in tests ONLY where no
   cross-source comparison happens; anything asserting ages, cutoffs or windows
   uses the same UTC clock as the code under test.
   The test JVMs themselves are pinned with `-Duser.timezone=UTC` (surefire +
   failsafe `argLine` in the root pom, mirroring the Dockerfile): since V46
   stores wall-clock columns as TIMESTAMPTZ, the JVM zone IS the stored clock,
   and a non-UTC host zone (e.g. +03:00 laptops) would otherwise shift every
   DB-read instant — this exact skew once made a 1-hour-old PENDING row look
   future-dated to the reaper cutoff.
3. New absolute-time fields use `Instant`; new DB columns use `TIMESTAMPTZ`
   (`token_revocations.expires_at` is the precedent). Existing `TIMESTAMP`
   columns stay and are UTC by this convention — including the
   `audit_logs.timestamp` partition key (guarded by V45).
4. All API datetimes are UTC, offset-less ISO-8601 (see OpenAPI description).
   JWT stays epoch-millis (already zone-free). `Duration`-based windows
   (`Transfer.cancel`, retention cutoffs) are zone-independent by construction.

**Alternatives rejected:**
- `systemDefaultZone` anywhere: makes laptop, CI and prod disagree; the exact
  failure mode that broke 5 scheduler/metric tests.
- Full `Instant` migration now: hundreds of files + data migration for zero
  behavioral gain while rule 1 holds. Revisit only if a second zone ever
  becomes a product requirement (it won't for a ledger).
