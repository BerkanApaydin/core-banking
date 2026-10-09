# DB-1: Native Enum Decision + Procedure

**Status:** V32 moved status/currency/role columns to native PG enums (single
source of truth). Reverting is expensive; the decision is sealed (V41).

**Accepted:** Rarely-changing columns keep the native enum
(`audit_logs.action`, `users.role`).

**Roadmap:** For evolvable columns such as `transfers.status` and
`accounts.status`, evaluate a return to `VARCHAR + CHECK` (new migration +
ADR; no data move, `USING status::text` + add CHECK).

**Procedure for adding a new enum value (mandatory):**
1. Measure the `ALTER TYPE ... ADD VALUE` lock time on staging (V32 note).
2. PG 12+ runs `ADD VALUE` inside Flyway's single migration transaction just
   fine as pure DDL (proven by V33 and V50 on PostgreSQL 15) — with one hard
   rule: the new value must not be *used* (inserted/compared) in the same
   transaction that adds it. A migration that both adds and uses a value must
   still split in two.
3. For rename/removal: new type + `ALTER COLUMN TYPE USING` + drop old type;
   run the lock-measurement step in `docs/release.md`.
4. CI applies migrations to an empty DB — the large-table simulation
   (`ci.yml` `migration-soak`) measures against a staging dump.
