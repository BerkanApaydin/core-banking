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
    & $Command @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

try {
    Push-Location -LiteralPath $repoRoot

    Invoke-Checked 'Python version' $python ($pythonPrefix + @('-c', "import sys; assert sys.version_info >= (3, 10), 'Python 3.10+ required'"))
    Invoke-Checked 'Docker daemon for Testcontainers' 'docker' @('info', '--format', '{{.ServerVersion}}')
    Invoke-Checked 'Java unit and integration tests with JaCoCo' (Join-Path $repoRoot 'mvnw.cmd') @('-B', '-ntp', 'clean', 'verify')
    Invoke-Checked 'Aggregate coverage gate' $python ($pythonPrefix + @('scripts/check_aggregate_coverage.py', 'app/target/site/jacoco-aggregate/jacoco.xml'))
    Invoke-Checked 'No Spring field injection in tests' $python ($pythonPrefix + @('scripts/check_test_injection.py'))
    Invoke-Checked 'Load-test runner unit tests' $python ($pythonPrefix + @('-m', 'unittest', 'load_tests.test_runner', '-v'))
    Invoke-Checked 'Load acceptance thresholds pinned' $python ($pythonPrefix + @('scripts/check_load_acceptance.py'))
    Invoke-Checked 'Health-smoke unit tests' $python ($pythonPrefix + @('-m', 'unittest', 'ops.test_health_smoke', '-v'))
    Invoke-Checked 'Restore-drill contract' $python ($pythonPrefix + @('scripts/check_restore_drill.py'))

    foreach ($script in @('i18n.js', 'idempotency.js', 'accounts.js', 'transfers.js', 'app.js')) {
        Invoke-Checked "JavaScript syntax: $script" 'node' @('--check', "app/src/main/resources/static/$script")
    }
    Invoke-Checked 'Browser idempotency tests' 'node' @('app/src/test/js/idempotency.test.js')
    Invoke-Checked 'Browser contract tests' 'node' @('app/src/test/js/frontend_contract.test.js')

    Write-Host "`nAll automated checks passed."
}
finally {
    Pop-Location
}
