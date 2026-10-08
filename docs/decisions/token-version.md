# G-2: Token Version (accepted stateless window)

**Decision:** The access-token path never hits the DB (stateless JWT). `role`
and `userId` come from the signed claims; a demotion/promotion therefore takes
effect on this path only after the access TTL (default 15 min). The refresh
path (`RefreshSessionUseCaseImpl`) compares `tokenVersion` against the DB and
closes the window there.

**Why accepted:** A per-request DB lookup would throw away every benefit of
stateless JWT (scale, latency); the 15-minute window plus short TTL plus
rotation (family revoke) bounds the risk. The comment in
`JwtAuthenticationFilter` documents this trade-off in code.

**Strict-mode flag (optional):** `app.security.strict-token-version-check=true`
may enable a DB lookup on the access path in the future (high-security
deployments). Default `false`; enabling it adds ~1 DB RTT to p99. This ADR
is the rationale for the default.

**Alternative (rejected):** `tokenVersion` lookup on every request — correct but
costs p99 + DB load; unnecessary on the rate-limited non-auth hot paths.
