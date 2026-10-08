# Starts the local dependencies and runs the Spring Boot app on the host.
# Settings live only for this invocation; no .env file or manual secret is needed.
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

    # Fresh secret first: docker compose interpolates the whole file
    # (including the unused `app` service, whose JWT_SECRET is mandatory),
    # so the secret must exist before any compose call, not just java.
    $secretBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($secretBytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)

    # Free port 8080 when switching from the all-in-Docker workflow.
    & docker compose stop app
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the Compose app container.' }
    & docker compose up -d --wait postgres redis
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL and Redis with Docker Compose.' }

    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }

    & (Join-Path $repoRoot 'mvnw.cmd') -pl app -am package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

    $jar = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app/target') -Filter 'app-*.jar' -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $jar) { throw 'Executable app JAR was not found.' }

    Write-Host 'Open http://localhost:8080/ (Ctrl+C to stop the app).'
    & java -jar $jar.FullName
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE." }
}
finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
