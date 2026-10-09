# Release automation (image digest pinning)

`k8s/bank-app.yaml` never deploys a mutable tag to production. Every
release records the exact `registry@sha256:digest` that staging
accepted, so a rollout, a rollback and a drill all point at the same
bytes.

## Flow

1. Tag the release: `git tag v0.0.2 && git push origin v0.0.2`.
   `.github/workflows/release.yml` builds the `Dockerfile`, pushes to
   GHCR (`ghcr.io/<owner>/bank-app:<tag>`), pins `k8s/bank-app.yaml`
   to the resolved digest (`bank-app@sha256:…`, verified with `grep`
   before upload) and attaches the pinned manifest plus
   `k8s/.image-digest` to the GitHub Release as files — not just notes.
2. For a manual/cluster-side pin (same result, no CI required):
   `scripts/pin-image-digest.sh ghcr.io/<owner>/bank-app:v0.0.2`
   (PowerShell: `scripts/pin-image-digest.ps1`). The script builds,
   pushes, resolves the digest with `docker inspect`, patches the
   `image:` line in `k8s/bank-app.yaml` in place and writes
   `k8s/.image-digest` (`<image>@sha256:<digest>`, UTC timestamp,
   git SHA).
3. Deploy the pinned manifest (`kubectl apply -f k8s/` after the
   Flyway Job completes), then run `python ops/health_smoke.py
   <staging-host> --require-probes --json` and save the JSON output
   next to the release tag as rollout evidence.
4. Rollback = re-apply the previous pinned manifest (previous digest
   in git history) after rehearsing it against the migrated schema
   (see `docs/operations.md` — rollback is forward repair, never a
   database downgrade).

## Rules

- `bank-app:latest` must never appear as an `image:` reference in a
  production manifest (CI K9/D7 guard enforces this); local compose builds
  from source instead of pulling tags. Production applies a manifest whose
  `image:` contains `@sha256:`.
- The digest file (`k8s/.image-digest`) is committed with every
  release, starting with the first cut (absent before any release exists);
  `git log -- k8s/.image-digest` is the release ledger.
- Staging restore/rolling drills record the digest they ran against
  (see `docs/disaster-recovery.md`); a drill without a digest proves
  nothing about the release.

## O-1/DB-1: large-table migration lock measurement (mandatory for V32-class migrations)

`ALTER TABLE ... TYPE` rewrites the table + holds ACCESS EXCLUSIVE. CI applies
migrations to an empty DB and can never catch this. Before any release that
contains `ALTER TABLE ... TYPE`, `ADD CONSTRAINT ... VALIDATE`, or
`CREATE INDEX` (non-concurrent):

1. Restore a production-size staging dump (≥1M `transfers` rows or 10% of prod,
   whichever is larger).
2. Run `scripts/migration_soak.sh` (seed + `flyway migrate` + lock-time report
   from `pg_locks`/`log_lock_waits`). Record wall time per statement.
3. Thresholds: any ACCESS EXCLUSIVE > 2s on `transfers`/`accounts`/`ledger_entries`
   blocks the release — rewrite as `NOT VALID + VALIDATE`, `pg_repack`, or
   batched backfill per `docs/decisions/enum-types.md`.
4. Attach the soak log to the release tag alongside the health-smoke JSON.
   CI `migration-soak` job enforces the script's presence (full soak runs on
   `workflow_dispatch` + release branches with a staging dump).
