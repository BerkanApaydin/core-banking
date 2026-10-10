# Arch-Test Hardening Round (invocation pinning + symmetry + structural gates)

## Status

Accepted — five ArchUnit additions, no production-code change except a
one-line `infrastructure/pom.xml` description fix. Verified green:
`mvn test -pl app -am -Dtest=com.bank.app.architecture.*Test`
(15 classes, 0 failures).

## Context

The suite pinned dependency *wiring* (`dependOnClassesThat`) and named
classes (`TransferDomainService` must call `Iban.checked`). Three gaps:

1. A wiring check passes with an unused import — dropping the
   `authorize...()` call inside a money-moving use case stayed green.
2. Adapter cross-coupling was checked from `account`/`transfer` sources only;
   a `user.adapter → account.adapter` import would have passed silently.
3. Checksum enforcement was pinned per callsite by class name; a future
   `BulkTransferUseCase` calling `Transfer.create` directly would bypass MOD
   97-10 without failing the build.

Separately, `infrastructure/pom.xml` still carried a compile-scope-looking
`account` edge in prose while the dependency itself had already been cut to
test-scope.

## Decision

1. **Invocation, not just wiring** (`WriteAuthorizationArchitectureTest`):
   `PlaceTransferUseCaseImpl`, `CancelTransferUseCaseImpl` and
   `CreateAccountUseCaseImpl` must *call* their authorization service. A
   one-hop same-class helper still passes (mirrors the
   `CacheInvalidationArchitectureTest` helper pattern); removing the call
   fails. The existing `dependOn` checks stay as the wiring layer.
2. **Four-way adapter symmetry** (`ModuleBoundariesArchitectureTest`):
   `user.adapter` and `audit.adapter` added as sources alongside `account`
   and `transfer`.
3. **Explicit adapter → infrastructure ban** (`LayeringArchitectureTest`):
   the layered-architecture rule stays loose for the composition root, so a
   dedicated rule pins the verified-zero state instead.
4. **Structural checksum gate** (`CodingRulesArchitectureTest`):
   only `TransferDomainService` may call `Transfer.create`. Legacy
   format-only reads never touch `create` and are unaffected.
5. **Infrastructure → account guard** (`ModuleBoundariesArchitectureTest`):
   production code in `infrastructure` may not touch `com.bank.app.account..`
   (test-scope IT slices are invisible to the production-only ArchUnit
   import). `pom.xml` description updated to say so.

## Consequences

- Future spendable-creation paths, authorization refactors and adapter
  additions fail the build at the exact invariant they break, instead of
  reopening a coupling silently.
- Helper-extraction refactors that keep the behavior stay green (no
  false-positive churn by design).

## Alternatives considered

- **`user-api`/`audit-api` extraction (rejected, deliberate):** `JwtPort`
  stays owned by the `user` BC (ISP), and `infrastructure` also needs
  `audit.domain` plus `audit.config` — moving ports alone leaves the domain
  leakage; moving the domain breaks BC ownership. Single-artifact monolith:
  the ArchUnit cage is sufficient. Revisit only on a real microservice
  split (see `infrastructure-split.md`).
- **Tightening the layered-architecture rule itself (rejected):** the
  Adapter↔Infrastructure edge is loose for the composition root on purpose;
  an explicit narrow rule pins the invariant without false positives.
