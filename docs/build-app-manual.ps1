# MANUAL.md をモバイル向け HTML に変換して Android アプリの assets に組み込む
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$manualDir = Join-Path $PSScriptRoot "manual"
$assetsDir = Join-Path $ProjectRoot "android\app\src\main\assets\manual"
$mdPath = Join-Path $manualDir "MANUAL.md"

$md = [System.IO.File]::ReadAllText($mdPath, [System.Text.Encoding]::UTF8)

# YAML frontmatter（PDF専用設定）を除去
$md = $md -replace '(?s)^---.*?---\s*', ''

# 表紙・目次・付録B（開発者向け）はアプリ内マニュアルでは不要なので除去
$md = $md -replace '(?s)<div class="cover">.*?</div>\s*<div class="page-break"></div>\s*', ''
$md = $md -replace '(?s)## 目次.*?(?=## 1\.)', ''
$md = $md -replace '(?s)## 付録B:.*?(?=\*本マニュアルは)', ''
$md = $md -replace '<div class="page-break"></div>\s*', ''

$tmpMd = Join-Path $env:TEMP "bandknife-app-manual.md"
$tmpBody = Join-Path $env:TEMP "bandknife-app-manual-body.html"
[System.IO.File]::WriteAllText($tmpMd, $md, (New-Object System.Text.UTF8Encoding($false)))

Push-Location $manualDir
try {
    npx --yes marked --gfm -i $tmpMd -o $tmpBody
    if ($LASTEXITCODE -ne 0) { throw "marked failed" }
}
finally { Pop-Location }

$body = [System.IO.File]::ReadAllText($tmpBody, [System.Text.Encoding]::UTF8)

$html = @"
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>バンドナイフ張力計 操作マニュアル</title>
<style>
body { font-family: sans-serif; font-size: 15px; line-height: 1.75; color: #1a1a2e; margin: 0; padding: 14px 14px 40px; background: #fdfdfe; }
h1 { font-size: 22px; border-bottom: 3px solid #1565c0; padding-bottom: 8px; color: #0d47a1; }
h2 { font-size: 19px; color: #0d47a1; border-left: 6px solid #1565c0; padding-left: 10px; margin-top: 1.8em; }
h3 { font-size: 16px; color: #1565c0; margin-top: 1.5em; }
table { border-collapse: collapse; width: 100%; font-size: 13px; margin: 10px 0; }
th { background: #e3f0fb; color: #0d47a1; text-align: left; }
th, td { border: 1px solid #b9cfe4; padding: 5px 8px; }
code { background: #eef2f7; padding: 1px 5px; border-radius: 3px; font-size: 13px; }
pre { background: #eef2f7; padding: 10px; border-radius: 6px; overflow-x: auto; font-size: 12px; }
blockquote { border-left: 4px solid #ff9800; background: #fff8ec; padding: 8px 14px; margin: 10px 0; }
img { max-width: 100%; height: auto; border: 1px solid #ccd6e0; border-radius: 10px; }
figure { margin: 12px 0; text-align: center; }
figcaption, .cap { display: block; font-size: 12px; color: #555; margin-top: 5px; text-align: center; }
/* PDF用の横並びレイアウトはスマホでは縦積みにする */
table.side, table.side tbody, table.side tr, table.side td { display: block; width: 100%; border: none; background: none; padding: 0; box-sizing: border-box; }
table.side td.side-img { text-align: center; margin-bottom: 8px; }
table.side td.side-img img { width: 230px; max-width: 70%; }
table.inner { display: table; width: 100%; }
table.inner tr { display: table-row; }
table.inner th, table.inner td { display: table-cell; border: 1px solid #b9cfe4; padding: 5px 8px; }
table.figs { display: table; width: auto; margin: 12px auto; }
table.figs tr { display: table-row; }
table.figs td { display: table-cell; border: none; background: none; padding: 2px 6px; text-align: center; vertical-align: top; }
table.figs img { width: 100%; }
/* 広告ページ要素 */
.promo-catch { font-size: 21px; font-weight: bold; color: #0d47a1; text-align: center; line-height: 1.45; margin: 14px 0 6px; }
.promo-lead { text-align: center; font-size: 14px; color: #37474f; margin: 0 8px 10px; }
table.promo, table.promo tbody, table.promo tr, table.promo td { display: block; width: 100%; border: none; box-sizing: border-box; }
table.promo td { background: #eaf3fc; border-radius: 12px; padding: 10px 12px; margin-bottom: 8px; font-size: 13px; line-height: 1.55; }
table.promo .pt { display: block; font-size: 15px; font-weight: bold; color: #0d47a1; margin-bottom: 3px; }
table.promo.stats { display: table; width: 100%; border-collapse: separate; border-spacing: 6px; }
table.promo.stats tbody { display: table-row-group; }
table.promo.stats tr { display: table-row; }
table.promo.stats td { display: table-cell; width: 33%; background: #0d47a1; color: #fff; text-align: center; padding: 8px 4px; margin: 0; }
table.promo.stats .num { display: block; font-size: 22px; font-weight: bold; line-height: 1.2; }
table.promo.stats .unit { font-size: 10px; opacity: .85; }
.promo-flow { text-align: center; background: #fff3e0; border-radius: 12px; padding: 10px 12px; font-size: 13px; color: #5d4037; margin-top: 8px; }
</style>
</head>
<body>
<h1>バンドナイフ張力計 操作マニュアル</h1>
$body
</body>
</html>
"@

New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $assetsDir "images") | Out-Null
[System.IO.File]::WriteAllText((Join-Path $assetsDir "index.html"), $html, (New-Object System.Text.UTF8Encoding($false)))
Copy-Item -Path (Join-Path $manualDir "images\*.png") -Destination (Join-Path $assetsDir "images") -Force

Remove-Item $tmpMd, $tmpBody -ErrorAction SilentlyContinue
$size = [math]::Round(((Get-ChildItem $assetsDir -Recurse | Measure-Object Length -Sum).Sum / 1MB), 2)
Write-Host "App manual built: $assetsDir ($size MB)"
