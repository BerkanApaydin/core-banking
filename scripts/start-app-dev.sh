#!/usr/bin/env bash
# Starts the local dependencies and runs the Spring Boot app on the host.
# Single command, zero setup: no .env file, no manual JWT secret needed.
# A fresh 256-bit JWT secret is generated per invocation (localhost only).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fail() { echo "start-app-dev: $1" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker not found. Install and start Docker Desktop: https://www.docker.com/products/docker-desktop"
docker compose version >/dev/null 2>&1 || fail "'docker compose' (v2.4+) not found. Update Docker Desktop."
# `docker compose version` is client-only: it succeeds while the daemon is
# down. Fail here with a readable message instead of a compose stack trace.
docker info >/dev/null 2>&1 || fail "Docker daemon is not running. Start Docker Desktop and retry."
command -v java >/dev/null 2>&1 || fail "Java 21 not found. Install Temurin 21 LTS."

export SPRING_PROFILES_ACTIVE=dev
export DB_HOST=localhost DB_PORT=5432 DB_USERNAME=bank_user DB_PASSWORD=bank_password
export REDIS_HOST=localhost REDIS_PORT=6389 SERVER_ADDRESS=127.0.0.1
# Fresh secret first: docker compose interpolates the whole file (including
# the unused `app` service, whose JWT_SECRET is mandatory), so the secret
# must exist before any compose call, not just java. Never printed, never
# persisted: each restart mints a new one and logs out browser sessions.
export JWT_SECRET="$(head -c 32 /dev/urandom | base64 | tr -d '\r\n')"

# Idempotent: stopping a non-running service must not kill the script.
docker compose stop app >/dev/null 2>&1 || true
docker compose up -d --wait postgres redis || fail "Could not start PostgreSQL and Redis. Inspect with 'docker compose logs postgres redis'."

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  # Fresh clones (or zip exports) may lack the exec bit even though the mode
  # is committed: repair it instead of failing with "Permission denied".
  [[ -x ./mvnw ]] || chmod +x ./mvnw 2>/dev/null || true
  [[ -x ./mvnw ]] || fail "./mvnw is not executable. Run: chmod +x mvnw"
  ./mvnw -pl app -am package -DskipTests || fail "Maven build failed."
else
  echo "SKIP_BUILD=1: reusing the existing JAR."
fi

jar=''
for candidate in app/target/app-*.jar; do
    [[ -f "$candidate" ]] || continue
    if [[ -z "$jar" || "$candidate" -nt "$jar" ]]; then
        jar="$candidate"
    fi
done
[[ -n "$jar" ]] || fail "Executable app JAR was not found (app/target/). Retry without SKIP_BUILD."

echo 'Open http://localhost:8080/ (Ctrl+C to stop the app).'
echo 'Dev path: Java runs on the host, PostgreSQL/Redis in Docker.'
echo 'All-in-Docker alternative: docker compose up --build --wait (stop this app first: ports 8080/5432/6389 are shared).'
echo 'NOTE (SEC-06): dev serves plain HTTP with non-Secure session cookies' \
    '(BROWSER_SESSION_SECURE=false). Session cookies minted here must never' \
    'be reused outside localhost; production enforces Secure cookies at boot.'
exec java -jar "$jar"
