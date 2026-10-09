# G-2: Token Version (accepted stateless window)

**Decision:** The access-token path never hits the DB (stateless JWT), except
on admin paths. `role` and `userId` come from the signed claims; a
demotion/promotion therefore takes effect on non-admin paths only after the
access TTL (default 15 min). Admin paths (`/api/v1/admin/**` plus
`/actuator/loggers/**`, SEC-01) re-validate the token's `ver` claim against
the user's current generation (the narrow `LoadUserPort.findTokenVersionById`
projection — one indexed PK read per admin call, no `User` aggregate crosses
into the platform filter) and fail closed (401 on mismatch or
deleted user, 503 when the user store is unreadable). The refresh path
(`RefreshSessionUseCaseImpl`) compares `tokenVersion` against the DB and
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

**O-3 acceptance (2026-10):** the window also covers money-movement paths (PlaceTransfer, CancelTransfer): they authorize the *resource owner* from the verified token without a per-request 	okenVersion lookup. Extending the DB check there would couple 	ransfer to the user-owned token lifecycle (a new cross-BC port on the request hot path, +1 DB RTT per transfer) for a 15-minute residual window on an already short-lived credential. Accepted: owner check + short TTL + refresh rotation + family revoke bound the risk; high-security deployments use the strict-mode flag instead of per-endpoint checks.
