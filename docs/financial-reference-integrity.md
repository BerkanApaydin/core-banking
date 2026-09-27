# Financial reference integrity decision

The application is one deployable process with one PostgreSQL database. Java modules retain scalar cross-context IDs so JPA entity associations do not create source-level coupling. PostgreSQL foreign keys protect the same references at rest.

Migration history records a change in that choice. V3 created the account-to-user and transfer-to-account foreign keys. V19 documented a no-FK direction, V22 removed those keys, and V24 restored them with `NOT VALID` followed by `VALIDATE CONSTRAINT`. V24 intentionally blocks rollout if old rows contain orphans; operators must inventory and repair those rows before retrying. The runtime orphan reporter now treats a nonzero count as evidence of disabled/broken enforcement or damaged/restored data, not as an expected consequence of the Java module boundary.

Do not edit historical migration comments or SQL after they have been applied: Flyway checksums protect that history. In particular, the V17 comment's “index-only scan” wording is stronger than its `(user_id, created_at DESC)` index can guarantee for `SELECT *`; it may avoid a sort but does not cover all selected columns. Verify plans with `EXPLAIN (ANALYZE, BUFFERS)` and index usage statistics before changing the live index set.
