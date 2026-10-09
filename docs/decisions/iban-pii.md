# IBAN PII: masked by default in messages and logs (O-12)

**Decision (2026-10):** raw IBAN strings never reach logs, exception messages
or API error responses. Every raw-String IBAN passes through
`IbanLogMask.mask` (first 8 + `*******` + last 4, mirroring
`Iban.toString()`; null/blank/short values collapse to `***`).

**Scope:**

- `Iban.toString()` was already masked; the leak was the raw-`String` paths:
  `AccountNotFoundException` (both the `account` domain and the `account-api`
  published variants), `DuplicateIbanException`, `AccountNotActiveException`,
  `AccountClosedException` and `SameAccountTransferException` embedded the
  full IBAN in both `args` (rendered via `messages.properties` `{0}`) and
  `defaultMessage`. All six now mask both.
- `InvalidIbanException` sites were audited: none embed raw values
  (`Iban` ctor messages are value-free; the checksum message uses the masked
  `this`).
- The `GET /accounts/iban/{iban}` path parameter itself is unavoidable in a
  REST lookup; with masked error bodies the remaining exposure is access-log
  path capture, which stays out of application logs by default
  (no access log is enabled; enabling one requires path masking at the
  reverse proxy).

**Rule:** new exceptions carrying identifiers must mask PII-bearing values at
construction (like the six above), never at the logging site — a forgotten
log statement must still be safe.
