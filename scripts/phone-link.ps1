# Links the Android phone to the backend over USB, and keeps the link alive.
# "adb reverse tcp:8080 tcp:8080" lets the app reach http://127.0.0.1:8080 on this computer. Two things break it:
#   - Android drops the link every time the phone reconnects (cable moved, phone restarted, USB mode changed).
#   - An old link from an earlier adb session can still hold port 8080 on the phone. A new link then fails with
#     "cannot bind listener: Address already in use", and the app says "No connection to Rinda".
# This script removes any old link before making a new one, restarts adb if the port is still held, and then
# checks from the phone itself that the backend answers. It never reports "linked" unless the phone got an answer.
#
# Usage, from the repo root:
#   powershell -ExecutionPolicy Bypass -File scripts\phone-link.ps1          # keep-alive window; Ctrl+C to stop
#   powershell -ExecutionPolicy Bypass -File scripts\phone-link.ps1 -Once    # link once; exit code 0 = working
param(
    [int]$Port = 8080,
    [int]$EverySeconds = 3,
    [switch]$Once
)
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found at $adb. Install the Android SDK platform tools." }

function Stamp { Get-Date -Format HH:mm:ss }

function Test-PhoneHasDevice {
    [bool](& $adb devices 2>$null | Select-String -Pattern "`tdevice$")
}

function Test-LinkListed {
    ((& $adb reverse --list 2>$null) -join ' ') -match "tcp:$Port tcp:$Port"
}

# Asks the backend's health endpoint from the phone itself, through the link. True only on an HTTP answer.
function Test-PhoneReachesBackend {
    $answer = & $adb shell "(printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n'; sleep 2) | toybox nc 127.0.0.1 $Port" 2>$null
    return (($answer -join ' ') -match 'HTTP/1\.[01] ')
}

function Test-BackendUp {
    try { Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3 | Out-Null; return $true } catch { return $false }
}

# Returns 'linked', 'linked-backend-down', 'nophone' or 'failed'.
function Connect-Phone {
    if (-not (Test-PhoneHasDevice)) { return 'nophone' }
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        # Clear an old link first: a stale one holds the port and makes the new one fail.
        & $adb reverse --remove "tcp:$Port" 2>$null | Out-Null
        & $adb reverse "tcp:$Port" "tcp:$Port" 2>$null | Out-Null
        if (Test-LinkListed) {
            if (-not (Test-BackendUp)) { return 'linked-backend-down' }
            if (Test-PhoneReachesBackend) { return 'linked' }
        }
        if ($attempt -eq 1) {
            # The port is still held on the phone: restarting adb releases the old listeners.
            Write-Host "$(Stamp)  The link didn't work; restarting adb and trying again..."
            & $adb kill-server 2>$null | Out-Null
            & $adb start-server 2>$null | Out-Null
            & $adb wait-for-device 2>$null | Out-Null
        }
    }
    return 'failed'
}

function Show-Result([string]$result) {
    switch ($result) {
        'linked' { Write-Host "$(Stamp)  Phone linked: the app reaches the backend at http://127.0.0.1:$Port." }
        'linked-backend-down' { Write-Host "$(Stamp)  Phone linked, but the backend isn't answering yet on port $Port. Is its window open?" }
        'nophone' { Write-Host "$(Stamp)  No phone connected. Plug it in by USB with USB debugging on, and allow this computer on the phone." }
        'failed' {
            Write-Host "$(Stamp)  FAIL: the phone can't reach the backend on port $Port." -ForegroundColor Red
            Write-Host '          Unplug the phone, plug it back in, then run this script again.'
            Write-Host '          If it still fails, another app on the phone is using port 8080: close it, or restart the phone.'
        }
    }
}

if ($Once) {
    $result = Connect-Phone
    Show-Result $result
    if ($result -eq 'linked' -or $result -eq 'linked-backend-down') { exit 0 } else { exit 1 }
}

$host.UI.RawUI.WindowTitle = 'Rinda phone link'
Write-Host "Keeping the phone linked to the backend on port $Port. Leave this window open. Ctrl+C to stop."
$state = ''
$sinceCheck = 0
while ($true) {
    $listed = (Test-PhoneHasDevice) -and (Test-LinkListed)
    # Every 30 seconds, also check end to end: a link can be listed and still not work.
    $sinceCheck += $EverySeconds
    $verify = $listed -and $state -eq 'linked' -and $sinceCheck -ge 30
    if ($verify) {
        $sinceCheck = 0
        if ((Test-BackendUp) -and -not (Test-PhoneReachesBackend)) { $listed = $false }
    }
    # After a failure, wait 30 seconds before trying again, so adb isn't restarted every few seconds.
    $waitAfterFailure = $state -eq 'failed' -and $sinceCheck -lt 30
    if ((-not $listed -or $state -ne 'linked') -and -not $waitAfterFailure) {
        $result = Connect-Phone
        if ($result -ne $state) { Show-Result $result }
        $state = $result
        $sinceCheck = 0
    }
    Start-Sleep -Seconds $EverySeconds
}
