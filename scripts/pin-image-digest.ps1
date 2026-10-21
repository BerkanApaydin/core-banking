# Pins k8s/bank-app.yaml to the exact pushed image digest (Windows).
# Usage: scripts\pin-image-digest.ps1 ghcr.io/<owner>/bank-app:v0.0.2
#        scripts\pin-image-digest.ps1 -SelfTest
param(
    [Parameter(Position = 0)][string]$ImageTag,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Windows PowerShell 5.1 Get-Content/Set-Content default to the ANSI code page,
# so a UTF-8 manifest round-trip can corrupt em dashes and Turkish text (and
# drop/convert a BOM). Always read and write UTF-8 explicitly, without a BOM:
# the checked-in YAML is UTF-8 no-BOM and YAML does not require a BOM.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )
    [System.IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}

function Update-ManifestImage {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Digest
    )

    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    # Capture the line ending so a CRLF checkout stays byte-for-byte CRLF.
    $pattern = '(?m)^([ \t]*)image:[^\r\n]*(?<eol>\r?)$'
    $imageLines = [regex]::Matches($text, $pattern)
    if ($imageLines.Count -ne 1) {
        throw "Expected exactly one image: line in $Path, found $($imageLines.Count)"
    }
    $updated = [regex]::Replace($text, $pattern, {
            param($match)
            "$($match.Groups[1].Value)image: $Digest$($match.Groups['eol'].Value)"
        })
    Write-Utf8NoBom -Path $Path -Text $updated
}

function Invoke-SelfTest {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("pin-image-digest-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $root | Out-Null
    try {
        # Keep this script ASCII-only: Windows PowerShell 5.1 parses BOM-less
        # .ps1 files with the ANSI code page, so a literal em dash/Turkish
        # text would be corrupted before the encoding test could run.
        $emDash = [string][char]0x2014
        $turkish = 'T' + [char]0x00FC + 'rk' + [char]0x00E7 + 'e'
        $original = "# release $emDash korunan $turkish metin`r`n          image: bank-app:0.0.1`r`n          # ikinci satir`r`n"
        $digest = 'registry.example/bank-app@sha256:' + ('a' * 64)

        foreach ($case in @('no-bom', 'bom')) {
            $fixture = Join-Path $root "$case.yaml"
            if ($case -eq 'bom') {
                $utf8Bom = New-Object System.Text.UTF8Encoding($true)
                [System.IO.File]::WriteAllText($fixture, $original, $utf8Bom)
            }
            else {
                Write-Utf8NoBom -Path $fixture -Text $original
            }

            Update-ManifestImage -Path $fixture -Digest $digest

            $bytes = [System.IO.File]::ReadAllBytes($fixture)
            if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
                throw "self-test ($case) failed: manifest was written with a UTF-8 BOM"
            }
            $verify = [System.IO.File]::ReadAllText($fixture, [System.Text.Encoding]::UTF8)
            if (-not $verify.Contains([string][char]0x2014)) {
                throw "self-test ($case) failed: em dash was not preserved"
            }
            if (-not $verify.Contains($turkish)) {
                throw "self-test ($case) failed: non-ASCII text was not preserved"
            }
            if (-not $verify.Contains($digest)) {
                throw "self-test ($case) failed: digest was not written"
            }
            if (-not $verify.Contains("`r`n")) {
                throw "self-test ($case) failed: CRLF line endings were not preserved"
            }
        }

        $empty = Join-Path $root 'no-image.yaml'
        Write-Utf8NoBom -Path $empty -Text "# no image here`n"
        $failedClosed = $false
        try {
            Update-ManifestImage -Path $empty -Digest $digest
        }
        catch {
            $failedClosed = $true
        }
        if (-not $failedClosed) {
            throw 'self-test failed: a manifest without an image: line was accepted'
        }

        Write-Host 'pin-image-digest self-test passed'
    }
    finally {
        Remove-Item -Recurse -Force $root -ErrorAction SilentlyContinue
    }
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

if ([string]::IsNullOrWhiteSpace($ImageTag)) {
    throw 'Usage: scripts\pin-image-digest.ps1 <registry-repo:tag> (or -SelfTest)'
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$manifest = Join-Path $repoRoot 'k8s/bank-app.yaml'
$digestFile = Join-Path $repoRoot 'k8s/.image-digest'

Push-Location -LiteralPath $repoRoot
try {
    docker build -t $ImageTag .
    if ($LASTEXITCODE -ne 0) { throw "docker build failed with exit code $LASTEXITCODE" }

    docker push $ImageTag
    if ($LASTEXITCODE -ne 0) { throw "docker push failed with exit code $LASTEXITCODE" }

    $digest = (docker inspect --format '{{index .RepoDigests 0}}' $ImageTag).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($digest)) {
        throw "Could not resolve RepoDigest for $ImageTag"
    }
    if ($digest -notmatch '^\S+@sha256:[0-9a-f]{64}$') {
        throw "Resolved digest is not an immutable sha256 reference: $digest"
    }

    Update-ManifestImage -Path $manifest -Digest $digest

    $gitSha = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitSha)) {
        throw 'Could not resolve the current git commit'
    }
    $pinnedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $digestLines = @($digest, "pinned_at=$pinnedAt", "git_sha=$gitSha") -join "`n"
    Write-Utf8NoBom -Path $digestFile -Text ($digestLines + "`n")

    Write-Host "Pinned $manifest to $digest"
    Write-Host "Recorded in $digestFile"
}
finally {
    Pop-Location
}
