# マニュアル一式を自動生成する
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$junction = "C:\bandknife-manual"

Write-Host "=== Generate Band Knife Manual ==="

if (-not (Test-Path $junction)) {
    cmd /c mklink /J $junction $ProjectRoot | Out-Null
}

Write-Host "[1/3] Android screenshots (Paparazzi)..."
Push-Location (Join-Path $junction "android")
.\gradlew.bat :app:recordPaparazziDebug --no-daemon
Pop-Location

Write-Host "[2/3] Copy images..."
& (Join-Path $PSScriptRoot "prepare-manual-images.ps1") -ProjectRoot $ProjectRoot

Write-Host "[3/3] Windows viewer screenshot..."
& (Join-Path $PSScriptRoot "capture-windows-viewer.ps1") -ProjectRoot $ProjectRoot

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
if ((Test-Path $adb) -and (& $adb devices | Select-String "device$")) {
    Write-Host "[optional] Device connected - capturing live screenshots..."
    & (Join-Path $PSScriptRoot "capture-manual-screenshots.ps1")
}

Write-Host ""
Write-Host "Manual: docs\manual\MANUAL.md"
Write-Host "Images: docs\manual\images\"
