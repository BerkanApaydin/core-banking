# Infrastructure Split Roadmap (infrastructure monolith → 4 modules)

**Problem (§3):** `infrastructure` holds ~84 classes / 10 concerns in a single
Maven unit. Change impact is wide; there is no compile-time gain.

**Target:**
```
infrastructure-web            (filters, handlers, versioning, CORS, ETag)
infrastructure-security       (chain, JWT, blacklist, CSRF, rate-limit, login-attempt)
infrastructure-outbox         (poller, processor, retention, relay, idempotency guard/aspect)
infrastructure-observability  (metrics, health, tracing, reconciliation job)
```

**Rules (after the split):**
- No infra module may depend on BC adapter concretes (existing rule stays).
- `infrastructure-outbox` may only depend on `common` + the port-owning BC APIs.
- The `app` composition root wires everything; BCs never see infra (DIP holds).

**Migration (without breaking):**
1. First seal the in-package boundaries with ArchUnit (new
   `InfrastructureSplitArchitectureTest`: `adapter.in.security..` ↔
   `adapter.in.outbox..` dependencies forbidden).
2. Physically split the Maven modules (move refactor, package names stable).
3. Add the 4 modules to the `app` pom; CI matrix unchanged.

**Completed since (prerequisites, no behavior change):**
- The `infrastructure` → `account` compile dependency is removed (test-scope
  only, for IT slices that need an `AccountApi` implementation at test
  runtime); production cross-context reads go via `account-api`. Pinned by
  `ModuleBoundariesArchitectureTest.infrastructureShouldNotDependOnAccountModule`
  (`com.bank.app.account..` does not match `com.bank.app.accountapi..` —
  ArchUnit matches package segments — so legitimate `account-api` usage keeps
  passing).
- BC adapters → `infrastructure` is explicitly forbidden
  (`LayeringArchitectureTest.adaptersShouldNotDependOnInfrastructure`; the
  layered-architecture rule stays loose for the composition root). Verified
  zero such dependencies before pinning.
- Adapter cross-coupling is now checked from all four bounded contexts
  (`account`, `transfer`, `user`, `audit` as sources), not just two.

**Deferred (deliberate):** no `user-api`/`audit-api` extraction. `JwtPort`
stays owned by the `user` BC (ISP), and `infrastructure` also needs
`audit.domain` (`AuditAction`, `AuditLog`) plus `audit.config` — moving only
the ports would leave the domain leakage in place, moving the domain would
break BC ownership. In this single-artifact monolith the ArchUnit cage is
sufficient; revisit only if a real microservice split is decided (see
`arch-test-hardening.md`).

**Cost:** ~2-3 days + CI verification. Prerequisite for team-split scale at Level 6.
