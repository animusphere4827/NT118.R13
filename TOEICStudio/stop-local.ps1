$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$sessionDir = [System.IO.Path]::GetFullPath((Join-Path $projectDir '../..'))
$taskWorkDir = Join-Path $sessionDir 'work'
$mysqlDir = Join-Path $taskWorkDir 'mysql-runtime/mysql-8.4.11-winx64'
$nodeCommand = Get-Command node -ErrorAction Stop
if (Test-Path (Join-Path $taskWorkDir 'app-process.json')) {
    $saved=Get-Content (Join-Path $taskWorkDir 'app-process.json') -Raw | ConvertFrom-Json
    $appProcess=Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
    if ($appProcess -and $appProcess.StartTime.Ticks -eq $saved.StartTicks -and $appProcess.Path -eq $saved.Path) {
        Stop-Process -Id $appProcess.Id
    }
}
# Gracefully stop only the database in this workspace; never kill another MySQL process.
if (Test-Path (Join-Path $taskWorkDir 'mysql-root.txt')) {
    $env:MYSQL_PWD = (Get-Content (Join-Path $taskWorkDir 'mysql-root.txt') -Raw).Trim()
    try { & (Join-Path $mysqlDir 'bin/mysqladmin.exe') --no-defaults -h 127.0.0.1 -P 3307 -u root shutdown } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
Write-Output 'Local app and MySQL stopped. Run start-local.ps1 to start them again.'
