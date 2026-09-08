$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "backend"
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$envFile = Join-Path $backend ".env"

Set-Location $backend

$password = Read-Host "MySQL root password"

& $mysql -u root "-p$password" -e "SELECT 1;" | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Wrong MySQL password."
  exit 1
}

(Get-Content $envFile) -replace '^DB_PASSWORD=.*', "DB_PASSWORD=$password" | Set-Content $envFile

npm run seed
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

node scripts/mock-devices.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

npm start
