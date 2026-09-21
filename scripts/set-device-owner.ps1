# Set Pandiyan Agency as Android Device Owner (phone or tablet).
# Run after factory reset, with USB debugging on, and NO Google/OEM accounts yet.

param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$adb = "adb"
$component = "com.mdmesh.agent/.policy.MeshDeviceAdminReceiver"
$pkg = "com.mdmesh.agent"

function AdbArgs {
    if ([string]::IsNullOrWhiteSpace($Serial)) { @() } else { @("-s", $Serial) }
}

Write-Host "Checking device..."
$devices = & $adb devices
Write-Host $devices

$prefix = AdbArgs
$state = & $adb @prefix get-state 2>$null
if ($state -ne "device") {
    throw "No device ready. Connect USB, enable debugging, accept the prompt."
}

Write-Host "Ensuring app is installed ($pkg)..."
$installed = & $adb @prefix shell pm path $pkg 2>$null
if (-not $installed) {
    $apk = Join-Path $PSScriptRoot "..\android\app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) {
        throw "APK not found at $apk. Build/install the app first (gradle installDebug)."
    }
    Write-Host "Installing $apk ..."
    & $adb @prefix install -r $apk
}

Write-Host "Setting Device Owner: $component"
& $adb @prefix shell dpm set-device-owner $component
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "FAILED. Typical fixes:"
    Write-Host "  1) Factory reset the device"
    Write-Host "  2) Skip Google / Xiaomi / Samsung account on first boot"
    Write-Host "  3) Re-enable USB debugging, reinstall APK, run this script again"
    Write-Host "See DEVICE-OWNER.md"
    exit 1
}

Write-Host ""
Write-Host "SUCCESS. Verify with:"
Write-Host "  adb shell dumpsys device_policy"
Write-Host "Open Pandiyan Agency — status should show Device Owner: ON"
