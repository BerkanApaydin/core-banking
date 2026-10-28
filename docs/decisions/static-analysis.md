# Static analysis gate (SpotBugs + structural checks)

## Status

Accepted 2026-10-28. Replaces the "no static analysis" finding in
`docs/reviews/2026-10-28-technical-audit.md` §16.

## Decision

`mvn verify` runs **SpotBugs 4.10.5** on every module with compiled classes
(POM-only modules such as the root and `migrations` log a no-files warning and
pass):

- `effort=Max` (deepest analysis)
- `threshold=Medium` (high + normal priority findings fail the build)
- `failOnError=true`
- one reviewed exclusion file: `config/spotbugs/exclude.xml`

The gate is bound to the standard lifecycle, so `mvn clean install` in CI and
`test-all.cmd` / `test-all.sh` fail on new findings without a separate CI step.
The report is written per module to `target/spotbugsXml.xml`.

`scripts/check_code_health.py` stays in place for rules SpotBugs does not
cover: method length, config-knob literals, stdout logging, wildcard imports,
ambient randomness/time (`Math.random`, `new Random`, `System.currentTimeMillis`,
`new Date`) and unbounded cached thread pools.

## Exclusion policy

Each entry in `config/spotbugs/exclude.xml` names the pattern and the reason.
Current exclusions:

| Pattern / target | Why it is not a defect |
|---|---|
| `EI_EXPOSE_REP2` | Constructor injection shares long-lived ports/adapters by design; there is no setter or field injection in main sources. Collection-owning DTOs are fixed with defensive copies and remain covered by `EI_EXPOSE_REP`. |
| `CT_CONSTRUCTOR_THROW` | Fail-fast validation in stateless Spring components; no finalizers or attacker-controlled subclasses. |
| `EQ_DOESNT_OVERRIDE_EQUALS` | `CustomUserDetails` inherits Spring Security `User` identity semantics; changing equals/hashCode would alter `SecurityContext` behavior. |
| `RequestBodySizeLimitFilter$BoundedRequestWrapper#getInputStream` / `EI_EXPOSE_REP` | Must return the same counting stream; recreating it would reset the byte cap. The stream is per-request, not shared state. |

`AbstractAccountSnapshotCache` no longer needs an exclusion. Its striped-lock
helper now normalizes the indices and keeps each `lock()`/`unlock()` pair in a
nested `try/finally`, so SpotBugs can see the release path. The `account-api`
report is 0 findings without a class-specific suppression.

A finding that describes a real defect falls outside this table and must be
fixed in code, not filtered. Exclusions are reviewed when the corresponding
design changes.

## Why Checkstyle, PMD and ErrorProne are not wired yet

The project must build offline from the local Maven cache. The cache contains
the Maven plugin wrappers for Checkstyle/PMD but **not** their engines
(`com.puppycrawl.tools:checkstyle`, `net.sourceforge.pmd:pmd-java`), and it has
no `error_prone_core`. Adding any of them would make the build depend on a
network fetch. The dependency-free Python gate covers the highest-value style
and structural rules meanwhile. Revisit this decision when the build may
resolve new artifacts; the intended next step is Checkstyle with a minimal
ruleset (imports, naming, whitespace) followed by PMD.

## Consequences

- New medium/high-priority SpotBugs findings fail `verify`.
- `EI_EXPOSE_REP2` remains excluded, so constructor-shared mutable collections
  are not machine-checked. Records that expose state must copy in their compact
  constructor (documented pattern: `PageResponse`, `AccountPage`, `HistoryPage`,
  `TransferReportResponse`, `ReportPage`, `*Properties`).
- Test sources are outside the gate; they run under JaCoCo/PIT and the
  framework-specific test contracts.
- SpotBugs is pinned to a cached version. Bumping it is a normal dependency
  change and must be done with the cache/network available.

## Verification

```bash
./mvnw -o -DskipTests compile
./mvnw -o spotbugs:check
python scripts/check_code_health.py
python scripts/check_code_health.py --self-test
```

Expected: `BUILD SUCCESS`, `BugInstance size is 0` per module, and
`static analysis ok`.
