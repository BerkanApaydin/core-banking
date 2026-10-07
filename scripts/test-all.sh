#!/usr/bin/env bash
# Runs the repository's automated checks from any working directory.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if command -v python3 >/dev/null 2>&1; then
    python_cmd=python3
else
    python_cmd=python
fi

run() {
    local label="$1"
    shift
    printf '\n==> %s\n' "$label"
    "$@"
}

run 'Python version' "$python_cmd" -c "import sys; assert sys.version_info >= (3, 10), 'Python 3.10+ required'"
run 'Docker daemon for Testcontainers' docker info --format '{{.ServerVersion}}'
run 'Java unit and integration tests with JaCoCo' ./mvnw -B -ntp clean verify
run 'Aggregate coverage gate' "$python_cmd" scripts/check_aggregate_coverage.py app/target/site/jacoco-aggregate/jacoco.xml
run 'No Spring field injection in tests' "$python_cmd" scripts/check_test_injection.py
run 'Load-test runner unit tests' "$python_cmd" -m unittest load_tests.test_runner -v
run 'Load acceptance thresholds pinned' "$python_cmd" scripts/check_load_acceptance.py
run 'Health-smoke unit tests' "$python_cmd" -m unittest ops.test_health_smoke -v

for script in i18n.js idempotency.js accounts.js transfers.js app.js; do
    run "JavaScript syntax: $script" node --check "app/src/main/resources/static/$script"
done
run 'Browser idempotency tests' node app/src/test/js/idempotency.test.js
run 'Browser contract tests' node app/src/test/js/frontend_contract.test.js

printf '\nAll automated checks passed.\n'
