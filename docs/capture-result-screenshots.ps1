param(
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.bandknife.tension",
    [string]$Activity = "com.bandknife.tension/.MainActivity",
    [int]$TapWaitSec = 120
)

$ErrorActionPreference = "Stop"
$OutDir = Join-Path $PSScriptRoot "manual\images"
$DumpLocal = Join-Path $env:TEMP "bandknife-ui-dump.xml"
$TapWav = Join-Path (Split-Path -Parent $PSScriptRoot) "test-sounds\audible-harmonics\00_listen_12th_24th_36th_90_180_269hz.wav"

$Labels = [pscustomobject]@{
    simple        = -join @([char]0x30B7, [char]0x30F3, [char]0x30D7, [char]0x30EB)
    advance       = -join @([char]0x30A2, [char]0x30C9, [char]0x30D0, [char]0x30A1, [char]0x30F3, [char]0x30B9)
    equipmentName = -join @([char]0x30DA, [char]0x30D5, [char]0x7528, [char]0x30B9, [char]0x30E9, [char]0x30A4, [char]0x30B5, [char]0x30FC, [char]0x0031, [char]0x53F7)
    saveAndFinish = -join @([char]0x8A18, [char]0x9332, [char]0x3057, [char]0x3066, [char]0x7D42, [char]0x4E86)
    passOk        = "OK"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Wait-Device { param([int]$Ms = 1200); Start-Sleep -Milliseconds $Ms }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & $Adb @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw ("adb failed: " + ($AdbArgs -join ' ')) }
}

function Get-UiDump {
    Invoke-Adb shell uiautomator dump /sdcard/window_dump.xml | Out-Null
    Invoke-Adb pull /sdcard/window_dump.xml $DumpLocal | Out-Null
    [xml](Get-Content -LiteralPath $DumpLocal -Encoding UTF8)
}

function Find-NodeByText {
    param([System.Xml.XmlElement]$Node, [string]$Text, [switch]$Partial)
    if ($Node.Name -eq "node") {
        $nodeText = $Node.GetAttribute("text")
        $contentDesc = $Node.GetAttribute("content-desc")
        $matched = if ($Partial) {
            ($nodeText -like "*$Text*") -or ($contentDesc -like "*$Text*")
        } else {
            ($nodeText -eq $Text) -or ($contentDesc -eq $Text)
        }
        if ($matched) { return $true }
    }
    foreach ($child in $Node.ChildNodes) {
        if (Find-NodeByText -Node $child -Text $Text -Partial:$Partial) { return $true }
    }
    return $false
}

function Test-UiText {
    param([string]$Text, [switch]$Partial)
    $dump = Get-UiDump
    return [bool](Find-NodeByText -Node $dump.hierarchy -Text $Text -Partial:$Partial)
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
    param([string]$Text, [switch]$Partial, [int]$Retries = 6)
    for ($i = 0; $i -lt $Retries; $i++) {
        $dump = Get-UiDump
        $point = Find-TapPoint -Node $dump.hierarchy -Text $Text -Partial:$Partial
        if ($point) {
            Invoke-Adb shell input tap $point.X $point.Y
            Wait-Device
            return
        }
        Wait-Device -Ms 600
    }
    throw ("UI element not found: " + $Text)
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

function Play-DeviceTapSounds {
    param([int]$Count = 5)
    $remote = "/sdcard/Download/bandknife-tap.wav"
    if (-not (Test-Path -LiteralPath $TapWav)) { return $false }
    Invoke-Adb push $TapWav $remote | Out-Null
    for ($i = 0; $i -lt $Count; $i++) {
        Invoke-Adb shell "am start -a android.intent.action.VIEW -d file://$remote -t audio/wav" | Out-Null
        Start-Sleep -Milliseconds 2200
    }
    return $true
}

function Play-PcTapSounds {
    param([int]$Count = 5)
    if (-not (Test-Path -LiteralPath $TapWav)) { return }
    Add-Type -AssemblyName System.Windows.Forms | Out-Null
    $player = New-Object System.Media.SoundPlayer $TapWav
    for ($i = 0; $i -lt $Count; $i++) {
        $player.PlaySync()
        Start-Sleep -Milliseconds 1800
    }
}

function Play-TapSounds {
    param([int]$Count = 5)
    Write-Host "  playing tap sounds from PC speaker..."
    Play-PcTapSounds -Count $Count
}

function Wait-For-ResultScreen {
    param([int]$TimeoutSec = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $avgLabel = -join @([char]0x5E73, [char]0x5747)
    while ((Get-Date) -lt $deadline) {
        if ((Test-UiText $Labels.saveAndFinish -Partial) -or (Test-UiText $Labels.passOk) -or (Test-UiText $avgLabel -Partial)) {
            Start-Sleep -Milliseconds 800
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Inject-TestTaps {
    Invoke-Adb shell am broadcast -a com.bandknife.tension.INJECT_MANUAL_TAPS -p $Package | Out-Null
    Wait-Device -Ms 1500
}

function Complete-SimpleMeasurement {
    Restart-App
    Tap-Text $Labels.simple
    Tap-Text $Labels.equipmentName -Partial
    Write-Host "  waiting for arming (3s)..."
    Start-Sleep -Seconds 3
    Write-Host "  injecting test taps..."
    Inject-TestTaps
    if (-not (Wait-For-ResultScreen -TimeoutSec 15)) {
        throw "Result screen not detected for simple mode"
    }
    Capture-Screen "03_simple_result"
}

function Complete-AdvanceMeasurement {
    Restart-App
    Tap-Text $Labels.advance
    Tap-Text $Labels.equipmentName -Partial
    Write-Host "  waiting for arming (3s)..."
    Start-Sleep -Seconds 3
    Write-Host "  injecting test taps..."
    Inject-TestTaps
    if (-not (Wait-For-ResultScreen -TimeoutSec 15)) {
        throw "Result screen not detected for advance mode"
    }
    Capture-Screen "04_advance_result"
}

Write-Host "=== Capture result screenshots (device) ==="
if (-not ((& $Adb devices) | Select-String "`tdevice$")) { throw "No adb device connected" }

Complete-SimpleMeasurement
Complete-AdvanceMeasurement

Write-Host ("Done. Images saved to: " + $OutDir)
