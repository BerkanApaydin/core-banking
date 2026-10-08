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

**Cost:** ~2-3 days + CI verification. Prerequisite for team-split scale at Level 6.
