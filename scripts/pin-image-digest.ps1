# Pins k8s/bank-app.yaml to the exact pushed image digest (Windows).
# Usage: scripts\pin-image-digest.ps1 ghcr.io/<owner>/bank-app:v0.0.2
param([Parameter(Mandatory = $true)][string]$ImageTag)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
$manifest = 'k8s/bank-app.yaml'
$digestFile = 'k8s/.image-digest'

docker build -t $ImageTag .
docker push $ImageTag
$digest = (docker inspect --format '{{index .RepoDigests 0}}' $ImageTag).Trim()
if (-not $digest) { throw "Could not resolve RepoDigest for $ImageTag" }

$lines = Get-Content $manifest
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*image:') { $lines[$i] = "          image: $digest" }
}
Set-Content $manifest $lines
@($digest, "pinned_at=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))", "git_sha=$((git rev-parse HEAD).Trim())") | Set-Content $digestFile
Write-Host "Pinned $manifest to $digest"
