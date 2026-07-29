# Paparazzi / 実機キャプチャ画像をマニュアル用ファイル名に整理する
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SnapshotDir = "",
    [string]$OutDir = (Join-Path $PSScriptRoot "manual\images")
)

$ErrorActionPreference = "Stop"

if (-not $SnapshotDir) {
    $SnapshotDir = Join-Path $ProjectRoot "android\app\src\test\snapshots\images"
}

$map = [ordered]@{
    "*01 simple equipment select*" = "01_simple_equipment_select.png"
    "*02 simple measuring*"        = "02_simple_measuring.png"
    "*03 simple result*"           = "03_simple_result.png"
    "*04 advance result*"          = "04_advance_result.png"
    "*05 detail measure*"          = "05_detail_measure.png"
    "*06 detail equipment*"        = "06_detail_equipment.png"
    "*07 detail history*"          = "07_detail_history.png"
    "*08 detail settings*"         = "08_detail_settings.png"
    "*09 spec settings*"           = "09_spec_settings.png"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-Path -LiteralPath $SnapshotDir)) {
    Write-Warning "Snapshot directory not found: $SnapshotDir"
    Write-Warning "Run: cd android && gradlew :app:recordPaparazziDebug"
    exit 1
}

foreach ($pattern in $map.Keys) {
    $file = Get-ChildItem -LiteralPath $SnapshotDir -Filter "*.png" | Where-Object { $_.Name -like $pattern } | Select-Object -First 1
    if ($file) {
        Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $OutDir $map[$pattern]) -Force
        Write-Host "  $($map[$pattern])"
    } else {
        Write-Warning "Missing snapshot: $pattern"
    }
}

Write-Host "Images ready: $OutDir"
