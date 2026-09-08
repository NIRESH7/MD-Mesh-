# Run this once as Administrator to set MySQL root password to: mdmesh123
$ErrorActionPreference = "Stop"
$init = Join-Path $PSScriptRoot "tmp-mysql-init.sql"
$defaults = "C:\ProgramData\MySQL\MySQL Server 8.0\my.ini"
$mysqld = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

@"
ALTER USER 'root'@'localhost' IDENTIFIED BY 'mdmesh123';
FLUSH PRIVILEGES;
"@ | Set-Content -Path $init -Encoding ASCII

Write-Host "Stopping MySQL80..."
Stop-Service MySQL80 -Force
Start-Sleep -Seconds 3

Write-Host "Applying new password..."
$p = Start-Process -FilePath $mysqld -ArgumentList @(
  "--defaults-file=`"$defaults`"",
  "--init-file=`"$init`"",
  "--console"
) -PassThru -WindowStyle Hidden

Start-Sleep -Seconds 12
if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

Write-Host "Starting MySQL80..."
Start-Service MySQL80
Start-Sleep -Seconds 5

& $mysql -u root -pmdmesh123 -e "SELECT 'PASSWORD_OK' AS status;"
if ($LASTEXITCODE -ne 0) { throw "Password reset failed" }

Write-Host ""
Write-Host "Done. MySQL root password is now: mdmesh123"
Write-Host "Press any key to close..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
