$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$sessionDir = [IO.Path]::GetFullPath((Join-Path $projectDir '../..'))
$record = Join-Path $sessionDir 'work/app-process.json'
if (-not (Test-Path -LiteralPath $record)) {
    Write-Output 'No saved local app process.'
    exit 0
}
$saved = Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
$appProcess = Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
if (-not $appProcess) {
    Write-Output 'The saved local app process is no longer running.'
    exit 0
}
$info = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $saved.Id)
$expectedPath = if ($saved.Path) { $saved.Path } else { (Get-Command node -ErrorAction Stop).Source }
if ($appProcess.StartTime.Ticks -ne $saved.StartTicks -or $info.ExecutablePath -ne $expectedPath -or
    $info.CommandLine -notmatch '--env-file=\.env\s+backend/server\.js$') {
    throw 'Process identity changed. No process was stopped.'
}
Stop-Process -Id $appProcess.Id -ErrorAction Stop
if (-not $appProcess.WaitForExit(10000)) { throw 'The local app did not stop in time.' }
Write-Output 'Local app stopped. PostgreSQL keeps running. Use start-local.ps1 to start the app again.'
