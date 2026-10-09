# Audit persistence: why AuditLogJpaEntity skips the shared auditing base

**Decision (2026-10):** `AuditLogJpaEntity` deliberately does NOT extend
`AuditableJpaEntity` (the shared `@MappedSuperclass` in the `persistence`
module), and the `audit` module deliberately does NOT depend on
`persistence`.

**Why:**

1. The audit trail is append-only evidence with its own time semantics: every
   row carries a domain `timestamp` (the business instant, set from the
   injected clock) plus an actor snapshot (`username`, nullable
   `actor_user_id` with no FK by design — the trail must survive user deletion
   and independent retention). `AuditableJpaEntity`'s
   `createdAt/createdBy/updatedAt/updatedBy` would duplicate three of those
   columns with subtly different meanings (wall-clock vs business instant),
   inviting exactly the timestamp confusion the time-strategy bans.
2. Keeping `audit` on `common` only preserves the supporting-subdomain
   boundary: audit persistence must be replaceable (e.g. WORM store,
   partitioned evidence tables) without inheriting the operational modules'
   auditing defaults.

**Rule:** if a future audit column needs auto-population, add an explicit
`@PrePersist` in `AuditLogJpaEntity` with a code comment — never extend the
shared base silently. Revisit only if retention automation requires the
shared auditing columns for tooling.
