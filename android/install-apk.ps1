$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $Root 'apk-paths.ps1')

$BuiltApk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
$DistApk = Get-DistApkPath -ProjectRoot (Split-Path $Root -Parent)
$InstallApk = Join-Path $env:TEMP 'bandknife-tension-install.apk'
$Adb = 'C:\platform-tools-latest-windows\platform-tools\adb.exe'

if (-not (Test-Path -LiteralPath $Adb)) {
    Write-Error "adb not found: $Adb"
    exit 1
}

if (Test-Path -LiteralPath $BuiltApk) {
    $SourceApk = $BuiltApk
} elseif (Test-Path -LiteralPath $DistApk) {
    $SourceApk = $DistApk
} else {
    Write-Error "APK not found. Run deploy.ps1 or gradlew.bat assembleRelease first."
    exit 1
}

# adb はパス内のハイフン（app-release.apk 等）をオプションと誤解釈することがあるため、
# 一時ファイル（ASCII・ハイフンなし）経由でインストールする。
Copy-Item -LiteralPath $SourceApk -Destination $InstallApk -Force

& $Adb devices
Write-Host ''
Write-Host "Installing from $InstallApk"
& $Adb install -r $InstallApk
exit $LASTEXITCODE
