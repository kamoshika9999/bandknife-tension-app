param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutFile = (Join-Path $PSScriptRoot "manual\images\10_windows_viewer.png")
)

$ErrorActionPreference = "Stop"
$viewerDir = Join-Path $ProjectRoot "windows-viewer"
$exe = Get-ChildItem -Path (Join-Path $viewerDir "bin") -Recurse -Filter "*.exe" -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -eq ".exe" -and $_.Name -notlike "createdump*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$sampleData = Join-Path $PSScriptRoot "manual\sample-data"
$autoDetect = Join-Path (Join-Path $env:USERPROFILE "My Drive") "bandknife-tension"

$csharp = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class WinCapture {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    public static void Capture(IntPtr handle, string path) {
        SetProcessDPIAware();
        ShowWindow(handle, 3); // SW_MAXIMIZE
        SetForegroundWindow(handle);
        System.Threading.Thread.Sleep(800);
        RECT r;
        GetWindowRect(handle, out r);
        int w = r.Right - r.Left; int h = r.Bottom - r.Top;
        using (var bmp = new Bitmap(w, h)) {
            using (var g = Graphics.FromImage(bmp)) {
                g.CopyFromScreen(r.Left, r.Top, 0, 0, new Size(w, h));
            }
            bmp.Save(path, ImageFormat.Png);
        }
    }
}
'@

Add-Type -TypeDefinition $csharp -ReferencedAssemblies System.Drawing

New-Item -ItemType Directory -Force -Path (Split-Path $OutFile) | Out-Null
New-Item -ItemType Directory -Force -Path $autoDetect | Out-Null
Copy-Item -LiteralPath (Join-Path $sampleData "records.csv") -Destination (Join-Path $autoDetect "records.csv") -Force

if (-not $exe) {
    Write-Host "Building Windows viewer..."
    Push-Location $viewerDir
    dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
    Pop-Location
    $exe = Get-ChildItem -Path (Join-Path $viewerDir "bin") -Recurse -Filter "*.exe" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if (-not $exe) { throw "Viewer executable not found" }

$env:BANDKNIFE_DATA_FOLDER = $autoDetect
$proc = Start-Process -FilePath $exe.FullName -PassThru
try {
    $deadline = (Get-Date).AddSeconds(25)
    do {
        Start-Sleep -Milliseconds 500
        $proc.Refresh()
    } while ($proc.MainWindowHandle -eq [IntPtr]::Zero -and (Get-Date) -lt $deadline)

    if ($proc.MainWindowHandle -eq [IntPtr]::Zero) { throw "Viewer window not found" }

    Start-Sleep -Seconds 2
    [WinCapture]::Capture($proc.MainWindowHandle, $OutFile)
    Write-Host "Captured viewer screenshot"
}
finally {
    if (-not $proc.HasExited) {
        $proc.CloseMainWindow() | Out-Null
        Start-Sleep -Milliseconds 800
        if (-not $proc.HasExited) { $proc.Kill() }
    }
}
