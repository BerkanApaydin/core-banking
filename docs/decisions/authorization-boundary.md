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

**Alternative (rejected):** `@PreAuthorize` only — visible but new prefixes open
silently; URL only — a prefix change drops the protection without the inner layer.
