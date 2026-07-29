# MANUAL.md を PDF に変換する
param(
    [string]$ManualDir = (Join-Path $PSScriptRoot "manual"),
    [string]$InputMd = "MANUAL.md",
    [string]$OutputPdf = "MANUAL.pdf"
)

$ErrorActionPreference = "Stop"
$mdPath = Join-Path $ManualDir $InputMd
$pdfPath = Join-Path $ManualDir $OutputPdf

if (-not (Test-Path -LiteralPath $mdPath)) {
    throw "Manual not found: $mdPath"
}

Push-Location $ManualDir
try {
    npx --yes md-to-pdf $InputMd
    if (-not (Test-Path -LiteralPath $pdfPath)) {
        throw "PDF generation failed"
    }
    $sizeMb = [math]::Round((Get-Item -LiteralPath $pdfPath).Length / 1MB, 2)
    Write-Host "Generated: $pdfPath ($sizeMb MB)"
}
finally {
    Pop-Location
}
