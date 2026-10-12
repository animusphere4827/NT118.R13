$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$sessionDir = [System.IO.Path]::GetFullPath((Join-Path $projectDir '../..'))
$taskWorkDir = Join-Path $sessionDir 'work'
$mysqlDir = Join-Path $taskWorkDir 'mysql-runtime/mysql-8.4.11-winx64'
$nodeCommand = Get-Command node -ErrorAction Stop
function Test-LocalPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}
if (-not (Test-Path (Join-Path $projectDir '.env'))) { throw 'Local .env is missing. Follow README.md to configure another machine.' }
if (-not (Test-LocalPort 3307)) {
    if (-not (Test-Path (Join-Path $mysqlDir 'bin/mysqld.exe'))) { throw 'Portable local MySQL is missing. Follow README.md to use your MySQL installation.' }
    $mysqlArgs = @('--no-defaults', "--basedir=$mysqlDir", "--datadir=$(Join-Path $taskWorkDir 'mysql-data')", '--port=3307', '--bind-address=127.0.0.1', '--mysqlx=OFF', '--console')
    Start-Process -FilePath (Join-Path $mysqlDir 'bin/mysqld.exe') -ArgumentList $mysqlArgs -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskWorkDir 'mysql-stdout.log') -RedirectStandardError (Join-Path $taskWorkDir 'mysql-stderr.log') | Out-Null
    for ($i=0; $i -lt 30 -and -not (Test-LocalPort 3307); $i++) { Start-Sleep -Milliseconds 500 }
    if (-not (Test-LocalPort 3307)) { throw 'MySQL did not start. Check work/mysql-stderr.log.' }
}
if (-not (Test-LocalPort 3000)) {
    $appProcess=Start-Process -FilePath $nodeCommand.Source -ArgumentList '--env-file=.env','backend/server.js' -WorkingDirectory $projectDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskWorkDir 'app-stdout.log') -RedirectStandardError (Join-Path $taskWorkDir 'app-stderr.log') -PassThru
    @{Id=$appProcess.Id;StartTicks=$appProcess.StartTime.Ticks;Path=$appProcess.Path} | ConvertTo-Json | Set-Content (Join-Path $taskWorkDir 'app-process.json')
}
for ($i=0; $i -lt 20; $i++) {
    try { $response=Invoke-RestMethod 'http://localhost:3000/api/health'; if($response.status -eq 'ok') { Write-Output 'TOEIC Studio is ready: http://localhost:3000'; exit 0 } } catch { Start-Sleep -Milliseconds 500 }
}
throw 'App did not start. Check work/app-stderr.log.'
