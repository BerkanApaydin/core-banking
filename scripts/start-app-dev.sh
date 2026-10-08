#!/usr/bin/env bash
# Starts the local dependencies and runs the Spring Boot app on the host.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

export SPRING_PROFILES_ACTIVE=dev
export DB_HOST=localhost DB_PORT=5432 DB_USERNAME=bank_user DB_PASSWORD=bank_password
export REDIS_HOST=localhost REDIS_PORT=6389 SERVER_ADDRESS=127.0.0.1
# Fresh secret first: docker compose interpolates the whole file (including
# the unused `app` service, whose JWT_SECRET is mandatory), so the secret
# must exist before any compose call, not just java.
export JWT_SECRET="$(head -c 32 /dev/urandom | base64 | tr -d '\r\n')"

docker compose stop app
docker compose up -d --wait postgres redis

./mvnw -pl app -am package -DskipTests

jar=''
for candidate in app/target/app-*.jar; do
    [[ -f "$candidate" ]] || continue
    if [[ -z "$jar" || "$candidate" -nt "$jar" ]]; then
        jar="$candidate"
    fi
done
if [[ -z "$jar" ]]; then
    echo 'Executable app JAR was not found.' >&2
    exit 1
fi

echo 'Open http://localhost:8080/ (Ctrl+C to stop the app).'
echo 'NOTE (SEC-06): dev serves plain HTTP with non-Secure session cookies' \
    '(BROWSER_SESSION_SECURE=false). Session cookies minted here must never' \
    'be reused outside localhost; production enforces Secure cookies at boot.'
exec java -jar "$jar"
