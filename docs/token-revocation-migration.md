# Token revocation backend migration

Production uses `TOKEN_BLACKLIST_BACKEND=hybrid` by default. Version 26 of the
Flyway schema adds `token_revocations`. The hybrid backend writes a SHA-256
digest of each newly revoked JWT to PostgreSQL and Redis, and accepts a token
only if neither store has an active revocation. PostgreSQL is queried on every
negative lookup; there is no negative cache or read-replica lookup. A database
read/commit failure returns 503 rather than accepting an unverified token.

## Rollout from the Redis-only backend

Do not switch directly from Redis-only to `database`: pre-existing Redis-only
revocations are absent from the new table. Apply V26 first and deploy the
hybrid backend to every pod. During a mixed-version rollout, new pods write to
both stores and read both stores; old pods can still see new revocations in
Redis. Confirm that the version being replaced already fails closed when
Redis is unavailable. If it does not, quiesce and remove those old pods before
accepting authenticated traffic on the new version.

After the last Redis-only writer is gone, wait at least the **longest JWT
lifetime issued by any old pod**, plus allowed clock skew, before selecting
`TOKEN_BLACKLIST_BACKEND=database`. Derive this interval from the actual old
`JWT_EXPIRATION` settings; the current production default of one hour is not
proof that no older token lives longer. Alternatively, rotate the JWT signing
key in a coordinated deployment and invalidate all old sessions, then switch
to database after old pods stop serving. A key rotation requires users to log
in again. Do not switch to database during a mixed rollout: old pods would not
see database-only writes.

The hybrid backend still requires Redis availability to check old revocations
and to write for old pods. A successful PostgreSQL write followed by a failed
Redis write returns 503; new pods deny the token but an old pod may not know
about it after Redis recovers. Retry logout after Redis recovery or remove old
pods. The UI reports an unconfirmed server-side logout when it receives 503.

## Database operation

`token_revocations` stores only `token_hash` and the signed JWT's absolute
`expires_at` time. Repeated logout uses an UPSERT that never shortens expiry.
An indexed primary-database `EXISTS` query runs for each authenticated request
in `database` mode, and for every negative local lookup in `hybrid` mode. This
adds database load to the authentication path; measure it with production-like
traffic and include it in the Hikari connection budget before scale-up.

Expired records are ignored by reads. A scheduled cleanup deletes at most
10,000 expired records per run, in 1,000-row statements, every five minutes by
default (`app.security.token-blacklist.cleanup-cron`). Monitor table size,
cleanup lag, read latency, and 503 counts. Back up and restore this table with
the application database; restoring an older database snapshot can lose later
revocations, so a restore procedure must also invalidate all tokens issued
after the snapshot (for example by signing-key rotation) before traffic resumes.

## JWT signing-key rotation

Rotation retires every outstanding session at once: all users log in again.
There is no dual-key overlap (single `jwt.secret`), so rotate in a
maintenance-light window, not mid-incident, unless the key is compromised
(compromise outranks convenience: rotate immediately, then handle the
re-login wave).

1. Generate: `JWT_SECRET="$(openssl rand -base64 32)"` — 32 bytes minimum
   when base64-decoded; anything shorter fails fast at boot with the exact
   bit count in the message.
2. Publish the new value to the secret manager (`bank-jwt-secret` in k8s;
   `JWT_SECRET` env in Compose). Never commit it; the dev default in
   `docker-compose.yml` is local-boot only and CI-pinned to the code constant
   (`scripts/check_compose_jwt_default.py`) precisely so drift fails loudly.
3. Rolling restart all pods. Mixed-version window is safe: old pods accept
   old-signed tokens, new pods accept new-signed ones; no shared revocation
   state depends on the key.
4. After the rollout, force re-login is automatic (old signatures no longer
   verify → 401 → login). Confirm `transfer.pending.reaped` and login rates
   return to baseline; watch for a 401 spike that does not decay (stuck
   clients caching the old token).
5. Alternative without rotation: bump per-user `tokenVersion` (role/password
   flows already do) to retire one identity's sessions at next refresh.

## R2: git-history secret hygiene (P10-1)

A previous default signing key was committed to git history and Docker images
(`JwtTokenProvider.DEFAULT_JWT_SECRET`; the value was rotated after discovery,
but history is immutable). Treat **every value that ever appeared in the
repository as compromised**:

1. Production must already run a generated `JWT_SECRET` (boot refuses the
   default twice: `JwtTokenProvider` with `allow-default-secret=false` and
   `ApplicationStartupValidator`). If any environment was ever started with a
   history value, rotate per the procedure above — the code guards prevent
   *new* prod boots on it, but they cannot retroactively invalidate tokens
   minted elsewhere.
2. Never "clean" this by rewriting history: the value is also baked into
   published images. Rotation, not erasure, is the fix.
3. `docker-compose.yml` still ships a dev default for local boot; the pin
   script (`scripts/check_compose_jwt_default.py`) keeps it equal to the code
   constant so drift fails loudly. That default is local-only by construction
   (prod refuses it); do not copy it into any real deployment.
