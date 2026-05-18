# run-maestro.ps1 — boot AVD, install debug APK, capture logs, run Maestro flows.
# Usage: pwsh scripts/run-maestro.ps1 [-AVD Medium_Phone_API_36]
[CmdletBinding()]
param(
    [string]$AVD = "Medium_Phone_API_36"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$logDir = Join-Path $repoRoot "build\maestro-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "$ts.log"

Write-Host "==> Booting AVD: $AVD"
$emulatorProc = Start-Process -PassThru -WindowStyle Hidden -FilePath "emulator" `
    -ArgumentList "-avd", $AVD, "-no-snapshot-save"
Write-Host "==> Waiting for device"
& adb wait-for-device
& adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'

Write-Host "==> Building debug APK"
if (Test-Path ".\gradlew.bat") {
    & .\gradlew.bat ":app:assembleDebug"
} else {
    & .\gradlew ":app:assembleDebug"
}
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed" }

Write-Host "==> Installing APK"
& adb install -r app\build\outputs\apk\debug\app-debug.apk

Write-Host "==> Starting log capture -> $logFile"
$logProc = Start-Process -PassThru -NoNewWindow -FilePath "lazylogcat" `
    -ArgumentList "logs", "dump", "--pkg", "com.github.jayteealao.crumbs" `
    -RedirectStandardOutput $logFile

Write-Host "==> Running Maestro flows"
$flows = @(
    "maestro\happy_path.yaml",
    "maestro\long_press.yaml",
    "maestro\filter_overlay.yaml",
    "maestro\sync_error.yaml"
)
& maestro test @flows
$maestroExit = $LASTEXITCODE

Write-Host "==> Stopping log capture"
if ($logProc -and -not $logProc.HasExited) {
    Stop-Process -Id $logProc.Id -ErrorAction SilentlyContinue
}

Write-Host "==> Stopping emulator"
& adb emu kill 2>$null
if ($emulatorProc -and -not $emulatorProc.HasExited) {
    Stop-Process -Id $emulatorProc.Id -ErrorAction SilentlyContinue
}

Write-Host "==> Done. Log: $logFile"
exit $maestroExit
