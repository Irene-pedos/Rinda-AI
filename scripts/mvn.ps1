# Runs the backend's Maven Wrapper on JDK 17, whatever the system default Java is.
# Example: powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 test
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw '.env not found. Run scripts\new-env.ps1 first.' }

$jdk = $null
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*JAVA_17_HOME=(.*)$') { $jdk = $Matches[1].Trim() }
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    throw "JDK 17 not found at '$jdk'. Check JAVA_17_HOME in .env."
}

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path

Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
