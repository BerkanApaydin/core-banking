# Financial reference integrity decision

The application is one deployable process with one PostgreSQL database. Java modules retain scalar cross-context IDs so JPA entity associations do not create source-level coupling. PostgreSQL foreign keys protect the same references at rest.

Migration history records a change in that choice. V3 created the account-to-user and transfer-to-account foreign keys. V19 documented a no-FK direction, V22 removed those keys, and V24 restored them with `NOT VALID` followed by `VALIDATE CONSTRAINT`. V24 intentionally blocks rollout if old rows contain orphans; operators must inventory and repair those rows before retrying. The runtime orphan reporter now treats a nonzero count as evidence of disabled/broken enforcement or damaged/restored data, not as an expected consequence of the Java module boundary.

Do not edit historical migration comments or SQL after they have been applied: Flyway checksums protect that history. In particular, the V17 comment's “index-only scan” wording is stronger than its `(user_id, created_at DESC)` index can guarantee for `SELECT *`; it may avoid a sort but does not cover all selected columns. Verify plans with `EXPLAIN (ANALYZE, BUFFERS)` and index usage statistics before changing the live index set.

> **One-time pre-1.0 exception (recorded, not a precedent):** twelve
> already-applied migrations (V8, V10, V14, V15, V20, V24, V28, V30, V32,
> V36, V38, V42, V43) gained a `SET LOCAL statement_timeout = '10min';`
> header plus comment-only clarifications, and V49/V50 were added, before
> any production deployment existed (0.0.1-SNAPSHOT, no prod history to
> protect). This changed their checksums: existing developer databases fail
> Flyway validation on next boot — recreate the dev volume
> (`docker compose down -v`) or run `flyway repair` rather than editing
> further. After 1.0 this rule has no exceptions.
