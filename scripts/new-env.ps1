# Creates .env from .env.example with new random secrets. Never overwrites an existing .env.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root '.env'

if (Test-Path $target) {
    Write-Host '.env already exists; leaving it unchanged.'
    exit 0
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng.GetBytes($bytes)
    return ([System.BitConverter]::ToString($bytes) -replace '-', '').ToLower()
}

$lines = Get-Content (Join-Path $root '.env.example') | ForEach-Object {
    if ($_ -match '__GENERATE__') { $_ -replace '__GENERATE__', (New-Secret) } else { $_ }
}

# Written without a BOM so every tool reads the first key correctly.
$text = ($lines -join "`n") + "`n"
[System.IO.File]::WriteAllText($target, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Created .env with new random secrets.'
