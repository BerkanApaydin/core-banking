# PostgreSQL ENUM Types — Deferred

V30 narrows the code columns (`status`, `currency`, `role`, `direction`,
`action`) from `VARCHAR(255)` to their real widths while the `CHECK`
constraints from V3/V6/V8/V28 keep enforcing the value sets.

The natural next step is native `CREATE TYPE ... AS ENUM` columns. It is
deferred deliberately, not overlooked:

1. The JPA entities map these columns as `String`. A native ENUM column
   rejects plain-VARCHAR bind parameters, so the entities must migrate to
   Java enums with `@Enumerated` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
   first (4 entities, 3 mappers, enum-parity tests).
2. ENUM value sets are append-only in practice: removing a value requires
   a table rewrite. New transfer/account states must go through the same
   review as a Java enum change.
3. Trigger: convert when row width measurably matters (TOAST pressure on
   `transfers`/`ledger_entries`) or when a new code column is added —
   whichever comes first. Do not convert pre-emptively on small tables.
