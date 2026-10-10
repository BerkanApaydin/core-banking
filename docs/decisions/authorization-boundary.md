# G-1: Authorization Boundary (two layers)

**Decision:** Admin protection has two layers; both are mandatory.

1. **Outer layer (URL boundary):** `SecurityConfig` contains
   `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`.
   A future endpoint that forgets the use-case role check stays closed.
2. **Inner layer (use case):** `SuspendAccountUseCaseImpl.requireAdmin()` and
   the `TransferAuthorizationService` ownership checks. Even if the URL layer
   is bypassed (e.g. a new prefix), the business layer still denies.

Both layers render the same `ACCESS_DENIED` problem body
(`ProblemDetailAccessDeniedHandler` ↔ `SecurityProblemHandler`), so clients
keep a single error parser. Foreign resources are hidden with 404 (no IDOR
oracle; G-5 reduces transfer-detail to the same shape).

**Test:** `AdminAuthorizationArchitectureTest` — `*AdminController` classes may
only reach admin use cases; runs together with `LayeringArchitectureTest`
(`allowEmptyShould(false)`) and the `ArchitectureTest` non-empty import guard.

Money-path authorization is pinned the same way, one level stronger:
`WriteAuthorizationArchitectureTest` checks not only the wiring
(`PlaceTransferUseCaseImpl` → `TransferAuthorizationService`) but the actual
invocation — an unused import satisfies `dependOnClassesThat` while dropping
the `authorize...()` call, so the call checks fail the build where the wiring
checks stay green. A one-hop same-class helper (e.g. a private
`authorize(...)` the use case extracts later) still passes, mirroring the
`CacheInvalidationArchitectureTest` helper pattern; removing the
authorization does not.

**Alternative (rejected):** `@PreAuthorize` only — visible but new prefixes open
silently; URL only — a prefix change drops the protection without the inner layer.
