$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkSrc = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
$ApkDst = Join-Path (Split-Path $Root -Parent) 'バンドナイフ張力計.apk'

Write-Host '=== Build release APK ==='
Push-Location $Root
try {
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Gradle build failed.'
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $ApkSrc)) {
    Write-Error "APK not found: $ApkSrc"
    exit 1
}

Write-Host ''
Write-Host '=== Copy APK to project root ==='
Copy-Item -LiteralPath $ApkSrc -Destination $ApkDst -Force
Write-Host $ApkDst

Write-Host ''
Write-Host '=== Install to connected device ==='
& (Join-Path $Root 'install-apk.ps1')
exit $LASTEXITCODE
