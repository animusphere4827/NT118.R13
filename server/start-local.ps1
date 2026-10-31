$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$sessionDir = [System.IO.Path]::GetFullPath((Join-Path $projectDir '../..'))
$taskWorkDir = Join-Path $sessionDir 'work'
New-Item -ItemType Directory -Force -Path $taskWorkDir | Out-Null
$nodeCommand = Get-Command node -ErrorAction Stop
function Test-LocalPort([string]$HostName, [int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect($HostName, $Port); return $true } catch { return $false } finally { $client.Dispose() }
}
$envFile = Join-Path $projectDir '.env'
if (-not (Test-Path $envFile)) { throw 'Local .env is missing. Copy .env.example to .env and follow README.md.' }
$dbLine = Get-Content $envFile | Where-Object { $_ -match '^DATABASE_URL=' } | Select-Object -First 1
if (-not $dbLine) { throw 'DATABASE_URL is missing in .env' }
$dbUri = [Uri]($dbLine -replace '^DATABASE_URL=', '')
$dbPort = if ($dbUri.Port -gt 0) { $dbUri.Port } else { 5432 }
if (-not (Test-LocalPort $dbUri.Host $dbPort)) { throw "PostgreSQL is not reachable at $($dbUri.Host):$dbPort. Start the PostgreSQL service first." }
if (-not (Test-LocalPort '127.0.0.1' 3000)) {
    $appProcess=Start-Process -FilePath $nodeCommand.Source -ArgumentList '--env-file=.env','backend/server.js' -WorkingDirectory $projectDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskWorkDir 'app-stdout.log') -RedirectStandardError (Join-Path $taskWorkDir 'app-stderr.log') -PassThru
    @{Id=$appProcess.Id;StartTicks=$appProcess.StartTime.Ticks;Path=$nodeCommand.Source} | ConvertTo-Json | Set-Content (Join-Path $taskWorkDir 'app-process.json')
}
for ($i=0; $i -lt 20; $i++) {
    try { $response=Invoke-RestMethod 'http://localhost:3000/api/health'; if($response.status -eq 'ok') { Write-Output 'TOEIC Studio is ready: http://localhost:3000'; exit 0 } } catch { Start-Sleep -Milliseconds 500 }
}
throw 'App did not start. Check work/app-stderr.log.'
