$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$configFile = Join-Path $workspace '.local/postgres.json'
if (-not (Test-Path -LiteralPath $configFile)) {
    throw 'PostgreSQL local configuration is missing. Install PostgreSQL 16 and configure DATABASE_URL.'
}
$config = Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
$control = Join-Path $config.Bin 'pg_ctl.exe'
& $control status -D $config.Data
if ($LASTEXITCODE -eq 0) {
    Write-Output 'PostgreSQL is already running.'
    exit 0
}
$process = Start-Process -FilePath $control -ArgumentList @('start', '-D', $config.Data, '-l', $config.Log, '-o', ('"-h 127.0.0.1 -p ' + $config.Port + '"'), '-w', '-t', '20') -WindowStyle Hidden -PassThru
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw 'PostgreSQL did not start. Check the local postgres log.' }
Write-Output ('PostgreSQL is ready on 127.0.0.1:' + $config.Port)
