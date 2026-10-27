$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$sessionDir = [System.IO.Path]::GetFullPath((Join-Path $projectDir '../..'))
$taskWorkDir = Join-Path $sessionDir 'work'
if (Test-Path (Join-Path $taskWorkDir 'app-process.json')) {
    $saved=Get-Content (Join-Path $taskWorkDir 'app-process.json') -Raw | ConvertFrom-Json
    $appProcess=Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
    if ($appProcess -and $appProcess.StartTime.Ticks -eq $saved.StartTicks -and $appProcess.Path -eq $saved.Path) {
        Stop-Process -Id $appProcess.Id
    }
}
Write-Output 'Local app stopped. PostgreSQL keeps running as its own service. Run start-local.ps1 to start the app again.'
