# Runs a private MySQL 8 server for Rinda on port 3307. No admin rights needed.
#   init    create the data folder, the rinda database and its user, then start
#   start   start the server (needed after each reboot)
#   stop    shut the server down
#   status  show whether it is running, and its version
# Data lives in %LOCALAPPDATA%\Rinda\mysql, outside the repo.
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('init', 'start', 'stop', 'status')]
    [string]$Action
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) {
    throw '.env not found. Run scripts\new-env.ps1 first.'
}

$cfg = @{}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Z0-9_]+)=(.*)$') { $cfg[$Matches[1]] = $Matches[2].Trim() }
}

$bin = $cfg['MYSQL_BIN']
$port = [int]$cfg['DB_PORT']
$mysqld = Join-Path $bin 'mysqld.exe'
$mysql = Join-Path $bin 'mysql.exe'
$mysqladmin = Join-Path $bin 'mysqladmin.exe'
if (-not (Test-Path $mysqld)) { throw "mysqld.exe not found in $bin. Check MYSQL_BIN in .env." }

$localDir = Join-Path $env:LOCALAPPDATA 'Rinda\mysql'
$dataDir = Join-Path $localDir 'data'
$ini = Join-Path $localDir 'my.ini'
$errorLog = Join-Path $localDir 'mysqld.log'

function Test-Port {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $wait = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        return ($wait.AsyncWaitHandle.WaitOne(500) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Start-Server {
    if (Test-Port) {
        Write-Host "A server is already listening on port $port."
        return
    }
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=`"$ini`"" -WindowStyle Hidden
    foreach ($i in 1..60) {
        if (Test-Port) {
            Write-Host "MySQL is listening on 127.0.0.1:$port."
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "MySQL did not start within 60 seconds. See $errorLog"
}

switch ($Action) {
    'init' {
        if (Test-Path $dataDir) {
            throw "$dataDir already exists, so nothing was changed. Use 'start'."
        }
        New-Item -ItemType Directory -Force $localDir | Out-Null

        $base = (Split-Path -Parent $bin) -replace '\\', '/'
        $iniText = @"
[mysqld]
port=$port
bind-address=127.0.0.1
basedir="$base"
datadir="$($dataDir -replace '\\', '/')"
log-error="$($errorLog -replace '\\', '/')"
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+00:00
"@
        [System.IO.File]::WriteAllText($ini, $iniText, (New-Object System.Text.UTF8Encoding($false)))

        $proc = Start-Process -FilePath $mysqld -Wait -PassThru -WindowStyle Hidden `
            -ArgumentList "--defaults-file=`"$ini`"", '--initialize-insecure'
        if ($proc.ExitCode -ne 0) { throw "mysqld --initialize failed. See $errorLog" }

        Start-Server

        $db = $cfg['DB_NAME']
        $user = $cfg['DB_USERNAME']
        $pass = $cfg['DB_PASSWORD']
        $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($cfg['MYSQL_ROOT_PASSWORD'])';
CREATE DATABASE IF NOT EXISTS ``$db`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$user'@'localhost' IDENTIFIED BY '$pass';
CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$pass';
GRANT ALL PRIVILEGES ON ``$db``.* TO '$user'@'localhost';
GRANT ALL PRIVILEGES ON ``$db``.* TO '$user'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
        $sql | & $mysql --protocol=TCP '--host=127.0.0.1' "--port=$port" -uroot
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the rinda database and user.' }
        Write-Host "Database '$db' and user '$user' are ready."
    }
    'start' {
        if (-not (Test-Path $dataDir)) { throw "No data folder yet. Run 'init' first." }
        Start-Server
    }
    'stop' {
        if (-not (Test-Port)) {
            Write-Host 'MySQL is not running.'
            break
        }
        $env:MYSQL_PWD = $cfg['MYSQL_ROOT_PASSWORD']
        & $mysqladmin --protocol=TCP '--host=127.0.0.1' "--port=$port" -uroot shutdown
        Remove-Item Env:MYSQL_PWD
        if ($LASTEXITCODE -ne 0) { throw 'mysqladmin shutdown failed.' }
        Write-Host 'MySQL stopped.'
    }
    'status' {
        if (-not (Test-Port)) {
            Write-Host "Nothing is listening on port $port."
            break
        }
        $env:MYSQL_PWD = $cfg['DB_PASSWORD']
        & $mysql --protocol=TCP '--host=127.0.0.1' "--port=$port" "-u$($cfg['DB_USERNAME'])" $cfg['DB_NAME'] -N -e 'SELECT VERSION();'
        Remove-Item Env:MYSQL_PWD
    }
}
