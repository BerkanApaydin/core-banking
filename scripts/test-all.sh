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
run 'Static repository contracts (CI parity)' "$python_cmd" scripts/check_ci_contracts.py
if command -v pwsh >/dev/null 2>&1; then
    run 'Image-pin encoding self-test' pwsh -NoProfile -File scripts/pin-image-digest.ps1 -SelfTest
elif command -v powershell >/dev/null 2>&1; then
    run 'Image-pin encoding self-test' powershell -NoProfile -ExecutionPolicy Bypass -File scripts/pin-image-digest.ps1 -SelfTest
else
    printf '\n==> Image-pin encoding self-test SKIPPED (PowerShell not installed)\n'
fi
run 'Docker daemon for Testcontainers' docker info --format '{{.ServerVersion}}'
run 'Java unit and integration tests with JaCoCo' ./mvnw -B -ntp clean install
run 'Aggregate coverage gate' "$python_cmd" scripts/check_aggregate_coverage.py app/target/site/jacoco-aggregate/jacoco.xml
run 'Kubernetes manifest contract' "$python_cmd" scripts/check_k8s_manifest_contract.py
run 'Migration chain contract' "$python_cmd" scripts/check_migration_chain.py
run 'No Spring field injection in tests' "$python_cmd" scripts/check_test_injection.py
run 'Load-test runner unit tests' "$python_cmd" -m unittest load_tests.test_runner -v
run 'Load acceptance thresholds pinned' "$python_cmd" scripts/check_load_acceptance.py
run 'Health-smoke unit tests' "$python_cmd" -m unittest ops.test_health_smoke -v
run 'Restore-drill contract' "$python_cmd" scripts/check_restore_drill.py
run 'Compose JWT default pinned' "$python_cmd" scripts/check_compose_jwt_default.py
run 'Error message keys in bundle' "$python_cmd" scripts/check_error_keys.py
run 'Migration statement timeouts' "$python_cmd" scripts/check_migration_timeouts.py
run 'Migration timeout guard self-test' "$python_cmd" scripts/check_migration_timeouts.py --self-test
run 'Code health (long methods, config literals)' "$python_cmd" scripts/check_code_health.py
run 'Code-health guard self-test' "$python_cmd" scripts/check_code_health.py --self-test
run 'Generate aggregate CycloneDX SBOM' ./mvnw -B -ntp -pl app org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom
if [ ! -s app/target/bom.json ]; then
    echo "SBOM was not generated at app/target/bom.json" >&2
    exit 1
fi
run 'Dependency review (OSV gate)' "$python_cmd" scripts/dependency_review.py
run 'Frontend dependencies' npm ci
run 'Frontend lint (ESLint)' npm run lint
run 'Frontend XSS guard' "$python_cmd" scripts/check_frontend_xss.py
run 'Frontend XSS guard self-test' "$python_cmd" scripts/check_frontend_xss.py --self-test

for script in storage.js i18n.js idempotency.js accounts.js transfers.js http.js reports.js detail.js format.js auth.js app.js; do
    run "JavaScript syntax: $script" node --check "app/src/main/resources/static/$script"
done
run 'Browser API contracts and idempotency (node:test)' node --test "app/src/test/js/*.test.js"

printf '\nAll automated checks passed.\n'
