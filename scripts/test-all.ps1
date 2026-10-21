# Runs the repository's automated checks from any working directory.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$python = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } else { 'python' }
$pythonPrefix = @()
if ($python -eq 'py') { $pythonPrefix = @('-3') }

function Invoke-Checked {
    param(
        [string]$Label,
        [string]$Command,
        [string[]]$CommandArgs
    )

    Write-Host "`n==> $Label"
    # Windows PowerShell 5.1 turns native stderr (JVM/Maven warnings) into
    # error records; with ErrorActionPreference=Stop that aborts the run even
    # though the command succeeded. Let native stderr through and judge the
    # command by its exit code instead.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Command @CommandArgs
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode."
    }
}

try {
    Push-Location -LiteralPath $repoRoot

    Invoke-Checked 'Python version' $python ($pythonPrefix + @('-c', "import sys; assert sys.version_info >= (3, 10), 'Python 3.10+ required'"))
    Invoke-Checked 'Static repository contracts (CI parity)' $python ($pythonPrefix + @('scripts/check_ci_contracts.py'))
    Write-Host "`n==> Image-pin encoding self-test"
    & (Join-Path $repoRoot 'scripts\pin-image-digest.ps1') -SelfTest
    if (-not $?) { throw 'Image-pin encoding self-test failed.' }
    Invoke-Checked 'Docker daemon for Testcontainers' 'docker' @('info', '--format', '{{.ServerVersion}}')
    Invoke-Checked 'Java unit and integration tests with JaCoCo' (Join-Path $repoRoot 'mvnw.cmd') @('-B', '-ntp', 'clean', 'install')
    Invoke-Checked 'Aggregate coverage gate' $python ($pythonPrefix + @('scripts/check_aggregate_coverage.py', 'app/target/site/jacoco-aggregate/jacoco.xml'))
    Invoke-Checked 'Kubernetes manifest contract' $python ($pythonPrefix + @('scripts/check_k8s_manifest_contract.py'))
    Invoke-Checked 'Migration chain contract' $python ($pythonPrefix + @('scripts/check_migration_chain.py'))
    Invoke-Checked 'No Spring field injection in tests' $python ($pythonPrefix + @('scripts/check_test_injection.py'))
    Invoke-Checked 'Load-test runner unit tests' $python ($pythonPrefix + @('-m', 'unittest', 'load_tests.test_runner', '-v'))
    Invoke-Checked 'Load acceptance thresholds pinned' $python ($pythonPrefix + @('scripts/check_load_acceptance.py'))
    Invoke-Checked 'Health-smoke unit tests' $python ($pythonPrefix + @('-m', 'unittest', 'ops.test_health_smoke', '-v'))
    Invoke-Checked 'Restore-drill contract' $python ($pythonPrefix + @('scripts/check_restore_drill.py'))
    Invoke-Checked 'Compose JWT default pinned' $python ($pythonPrefix + @('scripts/check_compose_jwt_default.py'))
    Invoke-Checked 'Error message keys in bundle' $python ($pythonPrefix + @('scripts/check_error_keys.py'))
    Invoke-Checked 'Migration statement timeouts' $python ($pythonPrefix + @('scripts/check_migration_timeouts.py'))
    Invoke-Checked 'Migration timeout guard self-test' $python ($pythonPrefix + @('scripts/check_migration_timeouts.py', '--self-test'))
    Invoke-Checked 'Code health (long methods, config literals)' $python ($pythonPrefix + @('scripts/check_code_health.py'))
    Invoke-Checked 'Code-health guard self-test' $python ($pythonPrefix + @('scripts/check_code_health.py', '--self-test'))
    Invoke-Checked 'Generate aggregate CycloneDX SBOM' (Join-Path $repoRoot 'mvnw.cmd') @('-B', '-ntp', '-pl', 'app', 'org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom')
    $bomPath = Join-Path $repoRoot 'app/target/bom.json'
    if (-not (Test-Path -LiteralPath $bomPath) -or (Get-Item -LiteralPath $bomPath).Length -eq 0) {
        throw "SBOM was not generated at app/target/bom.json"
    }
    Invoke-Checked 'Dependency review (OSV gate)' $python ($pythonPrefix + @('scripts/dependency_review.py'))
    Invoke-Checked 'Frontend dependencies' 'npm' @('ci')
    Invoke-Checked 'Frontend lint (ESLint)' 'npm' @('run', 'lint')
    Invoke-Checked 'Frontend XSS guard' $python ($pythonPrefix + @('scripts/check_frontend_xss.py'))
    Invoke-Checked 'Frontend XSS guard self-test' $python ($pythonPrefix + @('scripts/check_frontend_xss.py', '--self-test'))

    foreach ($script in @('storage.js', 'i18n.js', 'idempotency.js', 'accounts.js', 'transfers.js', 'http.js', 'reports.js', 'detail.js', 'format.js', 'auth.js', 'app.js')) {
        Invoke-Checked "JavaScript syntax: $script" 'node' @('--check', "app/src/main/resources/static/$script")
    }
    Invoke-Checked 'Browser API contracts and idempotency (node:test)' 'node' @('--test', 'app/src/test/js/*.test.js')

    Write-Host "`nAll automated checks passed."
}
finally {
    Pop-Location
}
