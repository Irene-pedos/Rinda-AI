# Checks the running Rinda stack the way the demo uses it, and prints PASS / FAIL / INFO for each step.
# Start everything first (scripts\start-dev.ps1). The backend must run with the dev profile.
#
# Usage, from the repo root:
#   powershell -ExecutionPolicy Bypass -File scripts\demo-check.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\demo-check.ps1 -SkipAnalysis
#
# It changes nothing except running the simulated sample analysis on demo plot 9001, which is safe to repeat.
# Exit code 1 if any step fails.
param(
    [string]$Api = 'http://127.0.0.1:8080/api/v1',
    [switch]$SkipAnalysis
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root 'logs\backend-dev.log'
$script:passed = 0
$script:failed = 0

function Pass([string]$what) { Write-Host "PASS  $what" -ForegroundColor Green; $script:passed++ }
function Fail([string]$what) { Write-Host "FAIL  $what" -ForegroundColor Red; $script:failed++ }
function Info([string]$what) { Write-Host "INFO  $what" -ForegroundColor Yellow }

function Check([string]$what, [scriptblock]$test) {
    try {
        $detail = & $test
        if ($detail -is [string] -and $detail.StartsWith('FAIL:')) {
            Fail "$what - $($detail.Substring(5).Trim())"
        } else {
            Pass ($(if ($detail) { "$what - $detail" } else { $what }))
        }
    } catch {
        Fail "$what - $($_.Exception.Message)"
    }
}

function Status-Of([scriptblock]$call) {
    try {
        & $call | Out-Null
        return 200
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return -1
    }
}

function Login([string]$phone) {
    try {
        Invoke-RestMethod -Method Post "$Api/auth/otp/request" -ContentType 'application/json' `
            -Body (@{ phone = $phone } | ConvertTo-Json) | Out-Null
    } catch {
        # 429: a code was sent less than 60 s ago. That code is still valid, so it is read from the log below.
        if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 429) { throw }
    }
    Start-Sleep -Milliseconds 700
    $escaped = [regex]::Escape($phone)
    $match = Select-String -Path $log -Pattern "\[DEV OTP\] phone=$escaped code=(\d{6})" | Select-Object -Last 1
    if (-not $match) { throw "no login code for $phone in $log" }
    $code = $match.Matches[0].Groups[1].Value
    $token = (Invoke-RestMethod -Method Post "$Api/auth/otp/verify" -ContentType 'application/json' `
        -Body (@{ phone = $phone; code = $code } | ConvertTo-Json)).accessToken
    return @{ Authorization = "Bearer $token" }
}

Write-Host "Rinda demo check against $Api"
Write-Host ''

# ----- Services -----
Check 'Backend is up' {
    $h = Invoke-RestMethod ($Api -replace '/api/v1$', '/actuator/health')
    if ($h.status -ne 'UP') { "FAIL: status $($h.status)" } else { 'UP' }
}
# The phone check asks the backend from the phone itself, so "No connection to Rinda" shows up here first.
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if ((Test-Path $adbPath) -and (& $adbPath devices 2>$null | Select-String -Pattern "`tdevice$")) {
    Check 'Phone over USB reaches the backend' {
        $answer = & $adbPath shell "(printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n'; sleep 2) | toybox nc 127.0.0.1 8080" 2>$null
        if (($answer -join ' ') -match 'HTTP/1\.[01] 200') { 'linked' }
        else { 'FAIL: no answer through the USB link. Run scripts\phone-link.ps1 -Once' }
    }
} else {
    Info 'No phone connected by USB, so the phone link was not checked.'
}
if (-not (Test-Path $log)) {
    Fail "No backend log at $log. Start the backend with the dev profile (scripts\start-dev.ps1)."
    exit 1
}

# ----- Logins (one per demo role) -----
$farmer1 = $null; $farmer2 = $null; $agent = $null; $officer = $null; $admin = $null
Check 'Farmer 1 logs in (+250780000001)' { $script:farmer1 = Login '+250780000001'; $null }
Check 'Farmer 2 logs in (+250780000002)' { $script:farmer2 = Login '+250780000002'; $null }
Check 'Agent logs in (+250780000003)'    { $script:agent = Login '+250780000003'; $null }
Check 'Officer logs in (+250780000004)'  { $script:officer = Login '+250780000004'; $null }
Check 'Admin logs in (+250780000005)'    { $script:admin = Login '+250780000005'; $null }
$farmer1 = $script:farmer1; $farmer2 = $script:farmer2; $agent = $script:agent
$officer = $script:officer; $admin = $script:admin

# ----- Farmer -----
if ($farmer1) {
    Check 'Farmer 1 sees 3 demo plots, labelled Demo' {
        $p = Invoke-RestMethod "$Api/plots" -Headers $farmer1
        if ($p.totalItems -ne 3) { "FAIL: $($p.totalItems) plots" }
        elseif (@($p.items | Where-Object { -not $_.isDemo }).Count -gt 0) { 'FAIL: a demo plot is not marked isDemo' }
        else { '3 plots' }
    }
    Check 'Farmer 1 sees plot 9001 crop health with confidence and a data note' {
        $s = Invoke-RestMethod "$Api/plots/9001/status" -Headers $farmer1
        if (-not $s.confidence) { 'FAIL: no confidence' } else { "$($s.status), confidence $($s.confidence)" }
    }
    Check 'Farmer 1 harvest forecast is a range or a reason, never one number' {
        $f = Invoke-RestMethod "$Api/plots/9001/forecast" -Headers $farmer1
        if ($f.available -and ($null -eq $f.lowPct -or $null -eq $f.highPct)) { 'FAIL: available without a range' }
        elseif ($f.available) { "$($f.lowPct)-$($f.highPct)% of normal" }
        else { "not available: $($f.reason)" }
    }
    Check 'Farmer 1 cover status' {
        $e = Invoke-RestMethod "$Api/plots/9001/enrollment" -Headers $farmer1
        "$($e.status), sum insured $(if ($null -eq $e.sumInsured) { 'not confirmed' } else { $e.sumInsured })"
    }
    Check 'Farmer 1 alerts and claims load' {
        $n = Invoke-RestMethod "$Api/notifications" -Headers $farmer1
        $c = Invoke-RestMethod "$Api/claims" -Headers $farmer1
        "$($n.totalItems) alerts, $($c.totalItems) claims"
    }
    Check 'A farmer cannot open the officer queue (403)' {
        $code = Status-Of { Invoke-RestMethod "$Api/officer/claims" -Headers $farmer1 }
        if ($code -ne 403) { "FAIL: got $code" }
    }
}
if ($farmer2) {
    # Demo plot 4 is unfinished on purpose: not walked yet, so no boundary and no satellite monitoring.
    Check 'Farmer 2 has 1 unfinished plot with no boundary yet' {
        $p = Invoke-RestMethod "$Api/plots" -Headers $farmer2
        $draft = @($p.items | Where-Object { $_.status -eq 'DRAFT' -and $_.monitoring.state -eq 'NO_BOUNDARY' }).Count
        if ($draft -lt 1) { "FAIL: $draft unfinished" } else { "$draft unfinished, monitoring NO_BOUNDARY" }
    }
}
Check 'No token gets 401' {
    $code = Status-Of { Invoke-RestMethod "$Api/plots" }
    if ($code -ne 401) { "FAIL: got $code" }
}

# ----- Agent -----
if ($agent) {
    Check 'Agent sees the cooperative farmers' {
        # Windows PowerShell 5.1 returns a JSON array as one item; piping unrolls it.
        $count = @(Invoke-RestMethod "$Api/agent/farmers" -Headers $agent | ForEach-Object { $_ }).Count
        if ($count -lt 2) { "FAIL: $count farmers" } else { "$count farmers" }
    }
    Check 'Agent can list field visits' {
        $v = Invoke-RestMethod "$Api/field-visits" -Headers $agent
        "$($v.totalItems) visits"
    }
}

# ----- Admin: ML service and analysis -----
if ($admin) {
    $mlUp = $false
    Check 'ML service is reachable from the backend' {
        $h = Invoke-RestMethod "$Api/admin/analysis-runs/ml-health" -Headers $admin
        if (-not $h.reachable) { "FAIL: $($h.detail)" }
        else {
            $script:mlUp = $true
            "model $($h.modelVersion), Earth Engine authorised: $($h.gee.authorised)"
        }
    }
    if ($script:mlUp -and -not $SkipAnalysis) {
        Check 'Run analysis (simulated sample on demo plot 9001) finishes' {
            $run = Invoke-RestMethod -Method Post "$Api/admin/analysis-runs" -Headers $admin -ContentType 'application/json' `
                -Body (@{ source = 'SAMPLE'; plotIds = @(9001) } | ConvertTo-Json)
            for ($i = 0; $i -lt 90 -and $run.status -in @('QUEUED', 'RUNNING'); $i++) {
                Start-Sleep -Seconds 2
                $run = Invoke-RestMethod "$Api/admin/analysis-runs/$($run.id)" -Headers $admin
            }
            if ($run.status -ne 'DONE' -or $run.plotsDone -ne 1) {
                "FAIL: run $($run.id) $($run.status): $($run.items[0].errorCode) $($run.items[0].error)"
            } else { "run $($run.id) DONE" }
        }
    } elseif ($SkipAnalysis) {
        Info 'Run analysis skipped (-SkipAnalysis).'
    }

    $thresholds = Invoke-RestMethod "$Api/admin/thresholds" -Headers $admin
    $missing = @($thresholds | Where-Object { $_.key -like 'trigger_*' -and $null -eq $_.value } | ForEach-Object { $_.key })
    if ($missing.Count -gt 0) {
        Info "No claim can start until the team supplies: $($missing -join ', ') (OPEN_QUESTIONS.md question 3). Enter them with PUT /admin/thresholds/{key}."
    } else {
        Pass 'Trigger values are set, so claims can start'
    }
}

# ----- Officer -----
if ($officer) {
    Check 'Officer dashboard summary and queue load' {
        $s = Invoke-RestMethod "$Api/officer/summary" -Headers $officer
        $q = Invoke-RestMethod "$Api/officer/claims" -Headers $officer
        "$($q.totalItems) claims, $($s.awaitingDecision) awaiting a decision"
    }
}

Write-Host ''
Write-Host "$script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
