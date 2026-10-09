# Dependency Review (OSV Gate + Accepted Risks)

**Gate:** `scripts/dependency_review.py` reads `target/bom.json` (CycloneDX),
queries OSV for all 158 libraries, and fails on HIGH/CRITICAL — same bar as
CI's `dependency-review-action` (fail-on-severity: high). 2026-10 run: 11 of
13 HIGH/CRITICAL findings closed by patch bumps
(`jackson-bom 2.21.7`, `tomcat 10.1.59`, `netty-bom 4.1.137.Final`,
`postgresql 42.7.12`).

**Accepted risks** (`scripts/dependency_allowlist.json`, re-review on Boot upgrade):
- `spring-webmvc:6.2.19` SSE + XsltView CRITICALs — fixes exist only on the
  7.x line (needs Spring Boot 4). Neither feature is used by this codebase
  (verified: no `SseEmitter`/`XsltView`/event-stream in main sources), so both
  are unreachable here. JSON-only API + static UI.

**Offline behavior:** when OSV is unreachable the script fails closed in CI
(`CI=true` → exit 1; the blocking `dependency-review-action` already ran
there) and reports-and-passes locally, so offline laptop runs stay usable.

**Notes:**
- `tomcat 10.1.58` was skipped upstream (never published/withdrawn); the first
  published fix is `10.1.59` — pin that, not `.58`.
- A forced-update (`-U`) full build once produced mass `NoClassDefFoundError`s
  in `account`/`transfer` test JVMs; a plain `clean verify` right after was
  fully green. Treated as a parallel-builder (`-T 2`) + metadata-refresh flake,
  not a code issue — but do not run `-U` and trust a single red run.
