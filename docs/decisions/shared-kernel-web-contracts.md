# Shared-kernel web contracts (R-C07)

## Decision

`ProblemBody`, `PublicApiPaths`, `ApiVersion`, `@Idempotent` and
`BrowserSessionCookies` stay in `common`
(`com.bank.app.common.adapter.in.*`). They are framework-free contracts and
helpers consumed by every bounded context's inbound adapter; `common` is the
shared kernel, so this is the dependency-correct home.

## Why not move them to infrastructure

`LayeringArchitectureTest.adaptersShouldNotDependOnInfrastructure` forbids any
BC adapter from depending on `infrastructure`. Moving these types there would
either break every BC adapter or force an illegal dependency edge. A separate
`web-support` module now exists for Spring-dependent RFC 7807 rendering
(`ProblemDetailFactory`, `BusinessErrorHttpMapper`), but the framework-free
contracts above stay in `common`: moving them to `web-support` would add a
dependency for types that every BC adapter can use without Spring. The
`adapter.in` package name describes the shared kernel's role, not a specific
bounded context's adapter.

## Consequences

- No dependency-direction change; the ArchUnit rule remains green.
- `web-support` is the home for Spring-dependent shared web rendering used by
  `infrastructure` and `user`; it depends only on `common`.
- A new cross-BC web contract belongs in `common` only if it is framework-free
  and carries no BC-specific policy. Otherwise it stays inside the owning BC
  or, if it needs Spring, in `web-support`.
- Revisit this decision if the shared surface grows enough to justify moving
  the framework-free contracts as well.
