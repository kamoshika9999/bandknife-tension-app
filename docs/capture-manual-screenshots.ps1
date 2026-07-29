param(
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.bandknife.tension",
    [string]$Activity = "com.bandknife.tension/.MainActivity"
)

$ErrorActionPreference = "Stop"
$OutDir = Join-Path $PSScriptRoot "manual\images"
$DumpLocal = Join-Path $env:TEMP "bandknife-ui-dump.xml"

# 日本語ラベル（文字化け防止のため Unicode コードポイントで生成）
$Labels = [pscustomobject]@{
    simple        = -join @([char]0x30B7, [char]0x30F3, [char]0x30D7, [char]0x30EB)
    advance       = -join @([char]0x30A2, [char]0x30C9, [char]0x30D0, [char]0x30F3, [char]0x30B9)
    detail        = -join @([char]0x8A73, [char]0x7D30)
    equipment     = -join @([char]0x8A2D, [char]0x5099)
    history       = -join @([char]0x5C65, [char]0x6B74)
    other         = -join @([char]0x305D, [char]0x306E, [char]0x4ED6)
    slicer        = -join @([char]0x30B9, [char]0x30E9, [char]0x30A4, [char]0x30B5)
    saveAndFinish = -join @([char]0x8A18, [char]0x9332, [char]0x3057, [char]0x3066, [char]0x7D42, [char]0x4E86)
    specSettings  = -join @([char]0x30B9, [char]0x30DA, [char]0x30C3, [char]0x30AF, [char]0x8A2D, [char]0x5B9A)
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Wait-Device { param([int]$Ms = 1200); Start-Sleep -Milliseconds $Ms }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & $Adb @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw ("adb failed: " + ($AdbArgs -join ' ')) }
}

function Get-UiDumpText {
    Invoke-Adb shell uiautomator dump /sdcard/window_dump.xml | Out-Null
    Invoke-Adb pull /sdcard/window_dump.xml $DumpLocal | Out-Null
    [System.IO.File]::ReadAllText($DumpLocal, [System.Text.Encoding]::UTF8)
}

function Get-UiDump {
    Invoke-Adb shell uiautomator dump /sdcard/window_dump.xml | Out-Null
    Invoke-Adb pull /sdcard/window_dump.xml $DumpLocal | Out-Null
    [xml](Get-Content -LiteralPath $DumpLocal -Encoding UTF8)
}

function Find-TapPoint {
    param([System.Xml.XmlElement]$Node, [string]$Text, [switch]$Partial)
    if ($Node.Name -eq "node") {
        $nodeText = $Node.GetAttribute("text")
        $contentDesc = $Node.GetAttribute("content-desc")
        $matched = if ($Partial) {
            ($nodeText -like "*$Text*") -or ($contentDesc -like "*$Text*")
        } else {
            ($nodeText -eq $Text) -or ($contentDesc -eq $Text)
        }
        if ($matched -and $Node.GetAttribute("bounds") -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            return @{
                X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            }
        }
    }
    foreach ($child in $Node.ChildNodes) {
        $found = Find-TapPoint -Node $child -Text $Text -Partial:$Partial
        if ($found) { return $found }
    }
    return $null
}

function Tap-Text {
    param([string]$Text, [switch]$Partial, [int]$Retries = 6, [switch]$ScrollOnMiss)
    for ($i = 0; $i -lt $Retries; $i++) {
        $dump = Get-UiDump
        $point = Find-TapPoint -Node $dump.hierarchy -Text $Text -Partial:$Partial
        if ($point) {
            Invoke-Adb shell input tap $point.X $point.Y
            Wait-Device
            return
        }
        if ($ScrollOnMiss) { Invoke-Adb shell input swipe 540 1600 540 800 300 | Out-Null }
        Wait-Device -Ms 600
    }
    throw ("UI element not found: " + $Text)
}

function Test-UiText {
    param([string]$Text)
    return (Get-UiDumpText).Contains($Text)
}

function Wait-UiText {
    param([string]$Text, [int]$TimeoutSec = 15)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-UiText $Text) { return $true }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Capture-Screen {
    param([string]$Name)
    $path = Join-Path $OutDir ($Name + ".png")
    $remote = "/sdcard/bandknife-manual-capture.png"
    Invoke-Adb shell "screencap -p $remote" | Out-Null
    Invoke-Adb pull $remote $path | Out-Null
    if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -lt 1000) {
        throw ("Screenshot failed: " + $Name)
    }
    Write-Host ("  captured: " + $Name + ".png")
}

function Restart-App {
    Invoke-Adb shell am force-stop $Package
    Wait-Device -Ms 500
    Invoke-Adb shell am start -n $Activity | Out-Null
    Wait-Device -Ms 2500
}

function Inject-TestTaps {
    & $Adb shell am broadcast -a com.bandknife.tension.INJECT_MANUAL_TAPS -p $Package | Out-Null
    Wait-Device -Ms 1500
}

function Enable-DemoStatusBar {
    & $Adb shell settings put global sysui_demo_allowed 1 | Out-Null
    & $Adb shell am broadcast -a com.android.systemui.demo -e command enter | Out-Null
    & $Adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000 | Out-Null
    & $Adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false | Out-Null
    & $Adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true | Out-Null
    & $Adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false | Out-Null
}

function Disable-DemoStatusBar {
    & $Adb shell am broadcast -a com.android.systemui.demo -e command exit | Out-Null
}

Write-Host "=== Band Knife Manual Screenshot Capture (device) ==="
if (-not ((& $Adb devices) | Select-String "`tdevice$")) { throw "No adb device connected" }

# マニュアル撮影用フックを有効化（adb からのみ設定可能）
Invoke-Adb shell settings put global bandknife_manual_mode 1 | Out-Null
Enable-DemoStatusBar

try {
    # --- シンプルモード ---
    Restart-App
    Tap-Text $Labels.simple
    Capture-Screen "01_simple_equipment_select"

    Tap-Text $Labels.slicer -Partial
    Start-Sleep -Seconds 4   # アーミング(2秒)完了を待つ
    Capture-Screen "02_simple_measuring"

    Inject-TestTaps
    if (-not (Wait-UiText $Labels.saveAndFinish 15)) { throw "Simple result screen not detected" }
    Capture-Screen "03_simple_result"
    Tap-Text $Labels.saveAndFinish   # 履歴に記録を残す

    # --- アドバンスモード ---
    Tap-Text $Labels.advance
    Tap-Text $Labels.slicer -Partial
    Start-Sleep -Seconds 4
    Inject-TestTaps
    if (-not (Wait-UiText $Labels.saveAndFinish 15)) { throw "Advance result screen not detected" }
    Capture-Screen "04_advance_result"
    Tap-Text $Labels.saveAndFinish -ScrollOnMiss

    # --- 詳細モード（残留する測定値を消すため再起動してから） ---
    Restart-App
    Tap-Text $Labels.detail
    Wait-Device
    Capture-Screen "05_detail_measure"

    Tap-Text $Labels.equipment
    Capture-Screen "06_detail_equipment"

    Tap-Text $Labels.history
    Capture-Screen "07_detail_history"

    Tap-Text $Labels.other
    Capture-Screen "08_detail_settings"

    # --- スペック設定（シンプルモードに戻ってから） ---
    Tap-Text $Labels.simple
    Tap-Text $Labels.specSettings
    Capture-Screen "09_spec_settings"

    Write-Host ("Done. Images saved to: " + $OutDir)
}
finally {
    Invoke-Adb shell settings put global bandknife_manual_mode 0 | Out-Null
    Disable-DemoStatusBar
}
