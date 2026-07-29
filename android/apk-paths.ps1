function Get-DistApkName {
    # ASCII-only script: build "バンドナイフ張力計.apk" without non-ASCII source literals
    return (-join @(
        [char]0x30D0, [char]0x30F3, [char]0x30C9, [char]0x30CA, [char]0x30A4, [char]0x30D5,
        [char]0x5F35, [char]0x529B, [char]0x8A08
    )) + '.apk'
}

function Get-DistApkPath {
    param([string]$ProjectRoot)
    Join-Path $ProjectRoot (Get-DistApkName)
}

function Remove-GarbledDistApks {
    param([string]$ProjectRoot)
    $correctName = Get-DistApkName
    Get-ChildItem -LiteralPath $ProjectRoot -Filter '*.apk' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne $correctName } |
        ForEach-Object {
            Write-Host "Removing garbled APK: $($_.Name)"
            Remove-Item -LiteralPath $_.FullName -Force
        }
}
