# Starts the local dependencies and runs the Spring Boot app on the host.
# Single command, zero setup: no .env file or manual secret is needed.
# A fresh 256-bit JWT secret is generated per invocation (localhost only).
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$settings = @{
    SPRING_PROFILES_ACTIVE = 'dev'
    DB_HOST                = 'localhost'
    DB_PORT                = '5432'
    DB_USERNAME            = 'bank_user'
    DB_PASSWORD            = 'bank_password'
    REDIS_HOST             = 'localhost'
    REDIS_PORT             = '6389'
    SERVER_ADDRESS         = '127.0.0.1'
}
$names = @($settings.Keys) + 'JWT_SECRET'
$previous = @{}
foreach ($name in $names) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    Push-Location $repoRoot

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker was not found. Install and start Docker Desktop: https://www.docker.com/products/docker-desktop'
    }
    try { & docker compose version | Out-Null } catch {
        throw "'docker compose' (v2.4+) was not found. Update Docker Desktop."
    }
    # `docker compose version` is client-only: it succeeds while the daemon is
    # down. Fail here with a readable message instead of a compose stack trace.
    & docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker daemon is not running. Start Docker Desktop and retry.' }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Java 21 was not found. Install Temurin 21 LTS.'
    }

    # Fresh secret first: docker compose interpolates the whole file
    # (including the unused `app` service, whose JWT_SECRET is mandatory),
    # so the secret must exist before any compose call, not just java.
    # Never printed, never persisted: each restart mints a new one and
    # logs out existing browser sessions.
    $secretBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($secretBytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)

    # Idempotent: the app container may not exist on first run.
    & docker compose stop app 2>$null
    & docker compose up -d --wait postgres redis
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL and Redis. Inspect with "docker compose logs postgres redis".' }

    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }

    if ($env:SKIP_BUILD -ne '1') {
        & (Join-Path $repoRoot 'mvnw.cmd') -pl app -am package '-DskipTests'
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    } else {
        Write-Host 'SKIP_BUILD=1: reusing the existing JAR.'
    }

    $jar = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app/target') -Filter 'app-*.jar' -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $jar) { throw 'Executable app JAR was not found. Retry without SKIP_BUILD.' }

    Write-Host 'Open http://localhost:8080/ (Ctrl+C to stop the app).'
    Write-Host 'Dev path: Java runs on the host, PostgreSQL/Redis in Docker.'
    Write-Host 'All-in-Docker alternative: docker compose up --build --wait (stop this app first: ports 8080/5432/6389 are shared).'
    Write-Host 'NOTE (SEC-06): dev serves plain HTTP with non-Secure session cookies (BROWSER_SESSION_SECURE=false). Session cookies minted here must never be reused outside localhost; production enforces Secure cookies at boot.'
    & java -jar $jar.FullName
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE." }
}
finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
