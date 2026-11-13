# Starts the whole Rinda stack for development or a demo, each part in its own window:
#   1. MySQL 8 (port 3307), if it isn't running yet
#   2. The backend (dev profile). Its window shows the login codes: [DEV OTP] phone=... code=...
#   3. The ML service (port 8000)
#   4. The USB link to a connected Android phone (scripts\phone-link.ps1: checked from the phone, kept alive)
# Then it waits until the backend and the ML service answer.
#
# Usage, from the repo root:
#   powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1 -SkipMl
# Stop everything by closing the backend and ML windows (or Ctrl+C in them).
param(
    [switch]$SkipMl
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Test-Port([int]$port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect('127.0.0.1', $port)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Wait-Http([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-RestMethod -Uri $url -TimeoutSec 5 | Out-Null
            return $true
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

if (-not (Test-Path (Join-Path $root '.env'))) {
    throw '.env not found. Run scripts\new-env.ps1 first (README step 3).'
}

# 1. MySQL
if (Test-Port 3307) {
    Write-Host '[1/4] MySQL is already running on port 3307.'
} else {
    Write-Host '[1/4] Starting MySQL on port 3307...'
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root 'database\mysql-dev.ps1') -Action start
}

# 2. Backend, in a window you can watch for login codes
if (Test-Port 8080) {
    Write-Host '[2/4] Something is already running on port 8080. Is the backend already started?'
} else {
    Write-Host '[2/4] Starting the backend in a new window (login codes appear there)...'
    $backend = "`$host.UI.RawUI.WindowTitle = 'Rinda backend - login codes appear here'; " +
            "Set-Location '$root'; " +
            "powershell -ExecutionPolicy Bypass -File scripts\mvn.ps1 spring-boot:run '-Dspring-boot.run.profiles=dev'"
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $backend
}

# 3. ML service
if ($SkipMl) {
    Write-Host '[3/4] Skipping the ML service (-SkipMl).'
} elseif (Test-Port 8000) {
    Write-Host '[3/4] Something is already running on port 8000. Is the ML service already started?'
} elseif (-not (Test-Path (Join-Path $root 'ml-service\.venv\Scripts\python.exe'))) {
    Write-Host '[3/4] The ML service is not set up yet (README step 7), so it was not started.'
} else {
    Write-Host '[3/4] Starting the ML service in a new window...'
    $ml = "`$host.UI.RawUI.WindowTitle = 'Rinda ML service'; " +
            "Set-Location '$root\ml-service'; " +
            ".venv\Scripts\python -m uvicorn app.main:app --port 8000"
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $ml
}

Write-Host ''
Write-Host 'Waiting for the backend (the first start can take a few minutes)...'
if (Wait-Http 'http://127.0.0.1:8080/actuator/health' 300) {
    Write-Host '  Backend:    http://localhost:8080/api/v1  (Swagger: http://localhost:8080/swagger-ui/index.html)'
} else {
    Write-Host '  The backend did not answer within 5 minutes. Look at its window for the error.'
}

# 4. Phone over USB, after the backend is up so the link can be checked from the phone itself.
# phone-link.ps1 clears a stale link first (the "Address already in use" failure) and never reports a link that
# doesn't work. Its keep-alive window then restores the link whenever the phone reconnects.
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$phoneLink = Join-Path $PSScriptRoot 'phone-link.ps1'
if (Test-Path $adb) {
    Write-Host '[4/4] Linking the phone over USB...'
    & powershell -ExecutionPolicy Bypass -File $phoneLink -Once
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', $phoneLink
    Write-Host '      A "Rinda phone link" window keeps the link alive. Leave it open while you test.'
} else {
    Write-Host '[4/4] adb not found; skipping the phone link.'
}
if (-not $SkipMl -and (Test-Path (Join-Path $root 'ml-service\.venv\Scripts\python.exe'))) {
    if (Wait-Http 'http://127.0.0.1:8000/health' 60) {
        Write-Host '  ML service: http://localhost:8000'
    } else {
        Write-Host '  The ML service did not answer. Look at its window for the error.'
    }
}
Write-Host ''
Write-Host 'Check everything works:  powershell -ExecutionPolicy Bypass -File scripts\demo-check.ps1'
