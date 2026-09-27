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
