# AUDIT-2: Tamper-evident audit hash chain

**Status:** partially implemented (2026-10). F1 is reduced, not closed. V68 and
`LEAST-PRIV-1` add the runtime/owner split, a 30-day retention floor, and an
empty-table signal. The opt-in external signed anchor now exports the chain head
outside the database; WORM/object-lock storage, KMS/HSM custody, and the
owner/superuser boundary remain operational open items.

**Decision:** every `audit_logs` row is linked to its predecessor by a SHA-256
hash computed inside PostgreSQL. A nightly verifier recomputes the chain and
publishes `audit.chain.broken`; retention deletes only the oldest chain prefix.
The application runs as `bank_app` with `SELECT`/`INSERT` only on audit rows,
while the chain trigger and retention function run as the owner. An opt-in
daily anchor job signs `(last_seq, last_hash)` with a dedicated HMAC key and
appends it to a file outside the database. This detects direct
`UPDATE`/`DELETE`/suffix-removal through the application role and continuity
breaks that survive a database-only rollback, but it is **not** a substitute
for a WORM sink or KMS/HSM key custody.

## Why

The V62 append-only trigger stops accidental application mutations, but an
actor with DDL rights can disable it, and a `DELETE` from the head leaves no
trace in the rows that remain. The review (F1) asked for tamper evidence that
survives a database-only compromise. A hash chain gives the cheapest useful
step: any rewrite or middle deletion breaks a recomputable link, and the
singleton chain head catches a removed suffix.

## Shape

- `audit_logs` gains `chain_seq BIGINT`, `prev_hash VARCHAR(64)`,
  `row_hash VARCHAR(64)` (`V67__audit_hash_chain.sql`).
- `trg_audit_logs_chain` (BEFORE INSERT) calls `audit_logs_assign_chain()`:
  - runs `SECURITY DEFINER` with `search_path = pg_catalog, public`, so the
    runtime role needs no `UPDATE` on `audit_chain_state`;
  - takes `SELECT ... FOR UPDATE` on the singleton `audit_chain_state` row;
  - reads `last_seq`/`last_hash`, assigns `chain_seq = last_seq + 1`;
  - computes `row_hash = sha256(canonical fields || prev_hash)`;
  - updates the state row in the same transaction.
- Canonical fields are `username`, `action`, `details`, `timestamp`,
  `actor_user_id`; values are coalesced and joined with `E'\x1f'`, and the
  timestamp is formatted in UTC with microsecond precision. Changing either
  the row or the separator/precision changes the hash.
- `audit_logs_first_broken_chain_seq()` returns the first broken `chain_seq`,
  `NULL` for a healthy chain, or `-1` for an empty table whose state head is
  non-zero:
  1. if the state singleton is missing, returns `-1` (the state is evidence);
  2. if the table is empty and `last_seq > 0`, returns `-1` (full wipe or full
     retention);
  3. compares `audit_chain_state.last_seq/last_hash` with the newest row
     (catches truncated suffixes and a rewritten head);
  4. walks the chain with `lag(row_hash)` and recomputes every row hash
     (catches rewrites and middle deletions);
  5. skips the `prev_hash` check for the oldest retained row, because prefix
     retention intentionally removes its predecessor.
- `delete_audit_logs_before(timestamp, batch)` runs `SECURITY DEFINER` with a
  pinned `search_path` and rejects a cutoff newer than 30 days. On the
  non-partitioned parent it finds the first retained row (`MIN(chain_seq) WHERE
  timestamp >= cutoff`) and deletes only rows with a smaller `chain_seq`. A
  backdated row inserted behind newer rows is therefore retained until every
  newer row has also fallen outside the cutoff (or the whole retained chain ages
  out), instead of punching a hole in the chain. This delays such rows; it does
  not lose the prefix guarantee.
- V69 extends the same function for the partitioned parent. It parses each
  child partition's upper bound and drops only partitions whose upper bound is
  at or before the cutoff **and** whose rows form a true chain prefix
  (`max(chain_seq)` in the candidates < `min(chain_seq)` in the retained rows).
  A backdated high-`chain_seq` row in an old timestamp partition blocks the
  drop; the function then falls back to the V67 prefix `DELETE`. Partition
  drops are metadata operations and are not bounded by `p_batch`. The branch is
  dormant until the one-time cutover moves `audit_logs` to a partitioned
  parent.
- `bank_app` has `SELECT`/`INSERT` on `audit_logs`, `SELECT` on
  `audit_chain_state`, and no `UPDATE`/`DELETE`/`TRUNCATE` or DDL. The
  capability roles are documented in
  `docs/decisions/least-privilege-db-roles.md`.
- `AuditChainVerificationScheduler` runs daily at 03:30
  (`app.audit.chain-verification-cron`, enabled by default) and publishes:
  - `audit.chain.broken` (0/1; page when 1);
  - `audit.chain.last_verified_epoch_seconds` (staleness alert after 48h).
- `AuditAnchorPublisher` runs daily at 03:45
  (`app.audit.anchor-cron`, **disabled by default**) under the
  `audit-anchor` advisory lock. It reads the singleton
  `audit_chain_state`, verifies the latest anchor signature, and appends a new
  anchor only when the head advanced. `AuditChainHeadAdapter` reads the head,
  a retained row hash, and the minimum retained sequence.
- The anchor line is
  `version|keyId|lastSeq|lastHash|publishedAtEpochSecond|signature`
  (version `1`; HMAC-SHA256; base64url signature). `AuditAnchorSigner` uses a
  dedicated key separate from the JWT key, supports a `key-id` plus
  `retired-keys` map, and fails closed on an unknown key id or bad signature.
- `FileAuditAnchorAdapter` appends with `O_APPEND`, an exclusive `FileLock`,
  `force(true)`, and POSIX `rw-------` where supported. The path must point at
  append-only/WORM storage controlled outside the database.
- The publisher publishes:
  - `audit.anchor.mismatches` (page when a mismatch occurs);
  - `audit.anchor.failures` (ticket when publication fails);
  - `audit.anchor.last_success_epoch_seconds` (staleness alert after 30h);
  - `audit.anchor.last_seq` and `audit.anchor.published`.

## What it detects

- direct `UPDATE` of a retained row (details, action, username, timestamp,
  actor, chain fields);
- deletion of a row from the middle of the retained chain;
- deletion of the newest row(s) while older rows remain (state head mismatch);
- a full `audit_logs` wipe while `audit_chain_state` still points at a
  non-zero head (`-1`);
- with the anchor enabled, a database head that is behind a signed anchor, a
  same-sequence hash mismatch, a rewritten retained prefix row, an invalid
  anchor signature, or an audit table that is empty while the head is non-zero.
  The mismatch alarm is a page and the publisher refuses to re-anchor.

## What it does not detect

- an actor who rewrites the whole chain **and** `audit_chain_state`
  consistently (database owner, superuser, `BYPASSRLS` role, or someone with
  both table and function DDL) **unless** the anchor is enabled and the actor
  cannot also rewrite the external sink;
- a full wipe of `audit_logs` + `audit_chain_state` cannot be distinguished
  from a deliberately reset database when no anchor exists: both report `-1`;
  with an anchor, the publisher reports a mismatch instead of resetting it;
- a compromised anchor HMAC key, or an actor who can replace the anchor file
  with a validly signed alternative;
- a non-WORM anchor file: deletion or truncation of the sink is outside the
  application's control (the next run may create a fresh file if the mount
  allows it). Production must use object-lock/WORM storage and KMS/HSM key
  custody.

**F1 remains open** for the owner/superuser case and the external anchor:
periodic export of `(last_seq, last_hash)` to object-lock storage or a
KMS-backed signature. The ordinary runtime-role path is now closed by
`LEAST-PRIV-1`: `bank_app` has no direct `UPDATE`/`DELETE`/`TRUNCATE` on audit
rows, no write access to `audit_chain_state`, and no DDL. Until an external
anchor exists this is evidence against ordinary application-path tampering and
accidental operator mistakes, not against a determined database administrator.

## Operational rules

- **Do not "repair" a broken chain in place.** Snapshot the database, preserve
  `audit_logs` + `audit_chain_state` + the external anchor file, and treat the
  alert as a security incident. Rebuilding hashes destroys the evidence.
- Do not re-anchor after `AuditAnchorMismatch`. Freeze writes, preserve the
  anchor sink, and compare the last valid anchor with the database backup.
- Enable the anchor only after `AUDIT_ANCHOR_FILE` points at a WORM/object-lock
  mount and `AUDIT_ANCHOR_SECRET` is a dedicated 32-byte base64 key. Keep the
  key out of `JWT_SECRET`; rotate via `key-id`/`retired-keys` and keep old keys
  verifiable until the anchors they signed are outside the retention window.
- Run the verifier manually with:
  `SELECT audit_logs_first_broken_chain_seq();`
  `NULL` means healthy at that instant; a number is the first suspect
  `chain_seq`; `-1` means an empty table with a non-zero state head (or a
  missing state row) and must be treated as a potential wipe.
- Retention is prefix-only by design and rejects a cutoff newer than 30 days.
  Backdated rows behind newer rows wait until every newer row also expires;
  this is expected, not data loss. A legitimate full retention pass can also
  leave the table empty with a non-zero head (`-1`); record the maintenance
  window and do not reset `audit_chain_state` to silence it. V68 revokes
  `EXECUTE` on the retention function from `PUBLIC`; a legacy non-owner
  retention role other than `bank_app` must be granted `EXECUTE` explicitly (or
  moved to `bank_audit_maintenance`) before deployment. V69 adds the
  partition-drop fast path, but only for partitions that are fully expired and
  chain-prefix-safe; the backdated-row fallback still uses bounded DELETE.
- A partition cutover must copy `chain_seq`/`prev_hash`/`row_hash`, recreate
  the append-only + chain triggers on the new parent, reset the state row, and
  reapply the audit/ledger grants and revokes; `scripts/audit_partition_cutover.sql`
  covers the data/trigger/state steps and prints the verifier result.
- The quarterly `bank-audit-partition-maintenance` CronJob keeps partitions
  ahead; see `docs/decisions/partitioning.md`.

## Rollout

V67 adds nullable columns, backfills every existing row in `id` order, then
sets the columns `NOT NULL`. It holds an `ACCESS EXCLUSIVE` lock on
`audit_logs` and temporarily disables the V62 append-only trigger during the
backfill, so it must run in the migration window with application writes
drained (the Flyway Job already precedes the Deployment). The backfill is one
`UPDATE` per row: on a large table, budget the maintenance window and watch
`statement_timeout` (V67 sets 10 minutes) before applying in production.

The anchor job is opt-in and has no migration. Roll it out after the database
chain is healthy: mount the WORM path, set `AUDIT_ANCHOR_ENABLED=true` and the
key material, run one job manually, verify the new line with the public
verification procedure, then leave the default 03:45 schedule in place. If the
anchor is enabled but the sink is not durable, the app reports failures but
cannot make an ordinary filesystem immutable.

## Cost

The chain serializes audit inserts on one state row. That is deliberate: the
hash must be computed against the latest committed head, and a gap-free
sequence requires a single writer. Audit volume is bounded by money/auth
events, not by reads; if the write-heavy path ever makes this row hot, the
next step is batching audit events per transaction or moving the anchor to an
append-only log — not weakening the chain. Measure before optimizing. The
anchor adds one read of the singleton head, one read of a retained row, and one
small append per day; the cost is negligible compared with the verifier.

## Verification

- `AuditChainVerificationAdapterTest` (unit) covers healthy/broken mapping.
- `AuditChainVerificationSchedulerTest` covers the two gauges; a failed
  verification leaves the last-success timestamp unchanged, so the 48h
  staleness alert (not a healthy 0) covers the gap.
- `AuditAnchorTest` (7 tests) covers canonical payload/line validation.
- `AuditAnchorSignerTest` (6 tests) covers sign/verify, tamper, unknown key,
  retired key, invalid base64, and short key rejection.
- `FileAuditAnchorAdapterTest` (3 tests) covers append/latest, missing file,
  and corrupt-line fail-closed behaviour.
- `AuditAnchorPropertiesTest` (4 tests) covers disabled/enabled validation and
  key-id/retired-key collisions.
- `AuditAnchorPublisherTest` (12 tests) covers new/genesis head, already
  anchored, DB-behind-anchor, same-sequence hash mismatch, retained-prefix
  rewrite, pruned/missing prefix, empty table, invalid signature, missing
  state, and sink failure.
- `AuditChainIntegrityIntegrationTest` (7 tests) covers chained inserts,
  rewrite detection, middle deletion, head deletion, prefix-only retention,
  chain-head adapter reads, and an empty table with a non-zero head against
  real PostgreSQL (V68 runs through Flyway in Testcontainers).
- `LeastPrivilegeDatabaseRoleIntegrationTest` (10 tests) proves the runtime
  role cannot update/delete/truncate audit rows, cannot write the chain head
  or run DDL, and can still insert, verify, and call guarded retention.
- `AuditPartitionRetentionIntegrationTest` (2 tests) proves V69 drops a fully
  expired partition and falls back to prefix `DELETE` when a backdated
  high-`chain_seq` row blocks the drop.
- Full `.\mvnw.cmd -B -ntp clean verify`: 3,779 tests (3,541 unit + 238 IT),
  0 failures/errors/skipped; aggregate coverage and SpotBugs gates passed.
