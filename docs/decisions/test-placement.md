# Integration-Test Placement — Decision

All Spring-context tests (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`,
Testcontainers-backed `*IntegrationTest`) live in the `app` module, in
packages mirroring the owning bounded context
(`com.bank.app.transfer...`, `com.bank.app.account...`, ...). Unit tests live
in their owning modules.

This is deliberate, not drift:

1. There is exactly one composition root (`BankApplication`) and one
   deployable. An integration test that boots a *different* context per
   module would validate wiring that never ships. Composition-root tests
   validate the wiring that actually runs in production.
2. Moving `@WebMvcTest` slices into BC modules would require each BC test
   scope to depend on `infrastructure` (for `ApiVersionConfig` and
   `GlobalExceptionHandler`), creating test-only module edges that mirror —
   and can silently diverge from — the production edges ArchUnit enforces.
3. The 33 `*IntegrationTest`/`*IT` classes already run against Testcontainers
   PostgreSQL via the shared `TestDatabaseContainer` (fresh database per JVM,
   Flyway-migrated, per-test cleanup in teardowns) — not against a shared
   developer database. Isolation is per-test, not per-module, which is the
   correct granularity here.

Revisit if a bounded context gains its own deployable: at that point the
context moves with it, and the composition-root suite shrinks to contract
tests between deployables.
