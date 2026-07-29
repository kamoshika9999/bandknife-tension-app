$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path $Root -Parent
$PublishDir = Join-Path $Root 'publish\portable'

Write-Host '=== Publish portable (self-contained single-file) ==='
Push-Location $Root
try {
    dotnet publish -c Release -r win-x64 --self-contained true `
        -p:PublishSingleFile=true `
        -o $PublishDir
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'dotnet publish failed.'
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

$ExeSrc = Get-ChildItem -LiteralPath $PublishDir -Filter '*.exe' | Select-Object -First 1
if (-not $ExeSrc) {
    Write-Error "EXE not found in: $PublishDir"
    exit 1
}

$ExeDst = Join-Path $ProjectRoot $ExeSrc.Name

Write-Host ''
Write-Host '=== Copy EXE to project root ==='
Copy-Item -LiteralPath $ExeSrc.FullName -Destination $ExeDst -Force
$sizeMb = [math]::Round((Get-Item -LiteralPath $ExeDst).Length / 1MB, 1)
Write-Host $ExeDst
Write-Host "Size: ${sizeMb} MB (no .NET runtime install required)"
