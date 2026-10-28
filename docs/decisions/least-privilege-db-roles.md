# LEAST-PRIV-1: Least-privilege database roles for audit and ledger

**Status:** partially implemented (2026-10). F1/F2 are reduced, not closed.

**Decision:** run the application as `bank_app`, a login role with
`SELECT`/`INSERT` on the append-only evidence tables and no DDL. Flyway keeps
validating as the schema owner through `spring.flyway.user`. Retention is a
`SECURITY DEFINER` function with a 30-day cutoff floor, the chain trigger runs
as the function owner, and the verifier reports `-1` when `audit_logs` is empty
but `audit_chain_state` still points at a non-zero head.

This closes the ordinary application-role path. It does **not** replace an
external anchor, KMS/HSM key custody, or protection against the schema owner.

## Why

V62/V65 made `audit_logs` and `ledger_entries` append-only at trigger level, and
V67 linked audit rows into a hash chain. Those guards are defense-in-depth only
while the runtime role cannot bypass them:

- a table owner can `ALTER TABLE ... DISABLE TRIGGER`;
- a role with `DELETE` can call the retention function with a future cutoff;
- a role with `UPDATE` on `audit_chain_state` can rewrite the head;
- a role with DDL can drop or recreate the guard objects.

Separating the runtime role from the schema owner removes those ordinary
application paths without changing the application code.

## Shape

| Role | Purpose | Key grants |
|---|---|---|
| `bank_app` | runtime login | `SELECT, INSERT` on `audit_logs` and `ledger_entries`; `SELECT` on `audit_chain_state`; ordinary CRUD on business tables; no DDL; no direct `UPDATE`/`DELETE`/`TRUNCATE` on evidence tables |
| `bank_audit_writer` | audit write capability | `SELECT, INSERT` on `audit_logs`; `SELECT` on `audit_chain_state`; granted to `bank_app` |
| `bank_audit_reader` | audit read capability | `SELECT` on `audit_logs` and `audit_chain_state`; verifier `EXECUTE` |
| `bank_audit_maintenance` | retention operator | `SELECT` on audit tables; `EXECUTE` on the retention function |
| `bank_ledger_maintenance` | ledger archive/test cleanup | `SELECT, DELETE` on `ledger_entries`; no `UPDATE` |

The companion script `scripts/provision_least_privilege_roles.sql` creates the
roles, grants the baseline DML, carves the evidence tables back to
insert/select-only, grants enum/sequence usage, and sets the runtime session
contract (`statement_timeout=30s`, `idle_in_transaction_session_timeout=60s`,
`timezone=UTC`). It is idempotent and does not store a password.

V68 hardens the privileged entry points:

- `audit_logs_assign_chain()` is `SECURITY DEFINER` with a pinned
  `search_path`, so `bank_app` needs no `UPDATE` on `audit_chain_state`.
- `delete_audit_logs_before(TIMESTAMP, INTEGER)` is `SECURITY DEFINER` with a
  pinned `search_path` and rejects a cutoff newer than 30 days. `PUBLIC`
  `EXECUTE` is revoked; `bank_app` and `bank_audit_maintenance` are granted
  explicitly.
- `audit_logs_first_broken_chain_seq()` returns `-1` when the table is empty
  but the state head is non-zero. A total table wipe (or a full retention pass)
  no longer looks like a fresh, empty chain.

Flyway is split from the runtime pool:

```yaml
spring:
  datasource:
    username: ${DB_USERNAME:bank_user}
    password: ${DB_PASSWORD}
  flyway:
    user: ${FLYWAY_USER:${DB_USERNAME:bank_user}}
    password: ${FLYWAY_PASSWORD:${DB_PASSWORD:}}
```

Both values default to the datasource identity, so single-role deployments are
unchanged. A least-privilege deployment sets `DB_USERNAME=bank_app` and
`FLYWAY_USER=<owner>`; the Flyway credential is used for validation (and
migration, if enabled) while Hikari connects as `bank_app`. If Flyway is
disabled in the application and the migration Job is the only writer,
`FLYWAY_*` is optional.

## What it protects

- `bank_app` cannot `UPDATE`, `DELETE`, or `TRUNCATE` audit or ledger rows.
- `bank_app` cannot disable the append-only/chain triggers.
- `bank_app` cannot update `audit_chain_state` directly.
- `bank_app` cannot create tables or alter the schema.
- The chain trigger and retention function run as the owner, so the runtime
  role needs no write grant on the state row and no direct `DELETE` on
  `audit_logs`.
- Retention cannot erase recent evidence: the cutoff must be at least 30 days
  old, and the batch size is bounded.
- An empty `audit_logs` with a non-zero state head is reported as broken
  (`-1`) instead of healthy `NULL`.

## What it does not protect

- The schema owner, superuser, or any role with DDL can still disable triggers,
  rewrite the chain, or rewrite `audit_chain_state` consistently.
- A full wipe of both `audit_logs` and `audit_chain_state` is not detectable
  without an external anchor (S3 Object Lock or a KMS-signed
  `(last_seq, last_hash)` export).
- The hash is unkeyed; there is no KMS/HSM signature yet.
- `bank_ledger_maintenance` can `DELETE` with the transaction-local
  `app.ledger_maintenance` flag. That is intentional for test/archive cleanup;
  it must not be granted to the runtime role.
- `ALTER DEFAULT PRIVILEGES` grants ordinary CRUD on future tables to
  `bank_app`. If `audit_logs`, `ledger_entries`, or `audit_chain_state` are
  recreated (for example by a partition cutover), re-run the carve-outs.
- The application can still call retention for rows older than 30 days. A
  separate scheduler datasource/role is a future hardening step.
- F2 reconciliation frequency and alert level are unchanged; this ADR covers
  the privilege path only.

## Operational rules

1. Run the script as the schema owner after migrations:

   ```bash
   psql "$OWNER_DATABASE_URL" -v ON_ERROR_STOP=1 \
        -f scripts/provision_least_privilege_roles.sql
   ```

2. Set the runtime password out of band; never commit it:

   ```sql
   ALTER ROLE bank_app PASSWORD '<from-secret-manager>';
   ```

3. Deploy with `DB_USERNAME=bank_app`, `DB_PASSWORD=<app secret>`,
   `FLYWAY_USER=<owner>`, `FLYWAY_PASSWORD=<owner secret>`.

4. Re-run the script after a migration batch that adds tables, and after any
   partition cutover or evidence-table recreation. Default privileges cover
   new tables, but the explicit revokes are per table.

5. Verify the post-condition:

   ```sql
   SELECT has_table_privilege('bank_app', 'audit_logs', 'UPDATE');   -- false
   SELECT has_table_privilege('bank_app', 'audit_logs', 'DELETE');   -- false
   SELECT has_table_privilege('bank_app', 'audit_logs', 'TRUNCATE'); -- false
   SELECT has_table_privilege('bank_app', 'ledger_entries', 'DELETE'); -- false
   ```

6. A partition cutover must recreate the audit/ledger triggers and reapply the
   grants/revokes; see `docs/decisions/partitioning.md` and
   `scripts/audit_partition_cutover.sql`.

## Rollout and backout

Rollout is additive: create the roles and grants, then switch the runtime
`DB_USERNAME` to `bank_app`. Flyway validation continues as the owner. Backout
is a configuration change: set `DB_USERNAME` and `FLYWAY_*` back to the owner
identity. The roles can remain; V68's function changes are backward compatible
for the owner and for `bank_app`.

V68 revokes `EXECUTE` on `delete_audit_logs_before` from `PUBLIC`. A deployment
that still runs retention as a legacy non-owner role other than `bank_app` must
grant that role `EXECUTE` before applying V68, or move it to
`bank_audit_maintenance`; otherwise retention calls fail closed. The owner and
`bank_app` are unaffected.

## Verification

- `LeastPrivilegeDatabaseRoleIntegrationTest` (10 tests) provisions the roles in
  the shared Testcontainers database and checks table privileges, audit/ledger
  append-only behavior, chain insert, verifier execution, retention guard and
  capability-role boundaries. Row-writing checks run in rolled-back
  transactions.
- `AuditChainIntegrityIntegrationTest` (6 tests) now includes
  `emptyTableWithNonZeroHeadIsReported`: insert a row in a transaction, delete
  all rows, and assert the verifier returns `-1`; the rollback restores the
  committed chain.
- Targeted run: 16/16 tests passed.
- Full `.\mvnw.cmd -B -ntp -o clean verify`: 3,774 tests (3,538 unit + 236 IT),
  0 failures/errors/skipped, 45 failsafe XML, aggregate coverage gates passed,
  SpotBugs 0.
- `python scripts/check_migration_chain.py` reports V1–V68 contiguous;
  `check_migration_timeouts.py` is clean.
