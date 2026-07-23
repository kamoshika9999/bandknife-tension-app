---
name: bandknife-android-deploy
description: >-
  バンドナイフ張力測定 Android アプリのリリースビルドと実機デプロイを行う。
  ユーザーが「ビルド」「デプロイ」「APK」「インストール」「実機に入れて」と言ったとき、
  または android/ の変更後に配布用 APK の更新が必要なときに使う。
---

# バンドナイフ張力測定 Android ビルド・デプロイ

## クイックスタート

ビルドと実機インストールを一括実行する:

```powershell
Set-Location "C:\システム開発\バンドナイフ張力測定\android"
.\deploy.bat
```

エージェントは **必ず自分でコマンドを実行** し、成功/失敗をログで確認してから完了報告する。

## スクリプト構成

| ファイル | 役割 |
|---------|------|
| `deploy.bat` | `deploy.ps1` を呼ぶラッパー（ASCII のみ） |
| `deploy.ps1` | ビルド → 配布用 APK コピー → インストール |
| `install-apk.bat` | `install-apk.ps1` を呼ぶラッパー |
| `install-apk.ps1` | 既存 APK の実機インストールのみ |

**`.bat` に日本語パスを書かない。** CMD は `バンドナイフ張力計.apk` を文字化けさせるため、コピーとパス解決は **必ず `.ps1`** で行う。

### 処理の流れ（deploy.ps1）

1. `gradlew.bat assembleRelease`
2. `app\build\outputs\apk\release\app-release.apk` をプロジェクトルートの `バンドナイフ張力計.apk` にコピー（PowerShell `Copy-Item -LiteralPath`）
3. `install-apk.ps1` を実行

### 処理の流れ（install-apk.ps1）

1. APK を探す（優先順）:
   - `android\app\build\outputs\apk\release\app-release.apk`
   - `..\バンドナイフ張力計.apk`
2. **一時ファイル** `%TEMP%\bandknife-tension-install.apk` にコピー
3. `adb install -r` で一時ファイルをインストール

**adb に直接 `app-release.apk` を渡さない。** ファイル名の `-release` が adb のオプションと誤認識され `Can't open file: -S` 等で失敗する。一時ファイル名はハイフンなしの ASCII のみ。

## ワークフロー

```
Task Progress:
- [ ] 1. deploy.bat を実行
- [ ] 2. ビルド成功を確認（BUILD SUCCESSFUL）
- [ ] 3. プロジェクトルートに バンドナイフ張力計.apk がコピーされたことを確認
- [ ] 4. adb devices で端末接続を確認
- [ ] 5. インストール成功を確認（Success）
```

### コマンド早見表

| 目的 | コマンド |
|------|---------|
| ビルド＋コピー＋インストール | `.\deploy.bat` |
| インストールのみ | `.\install-apk.bat` |
| ビルドのみ | `.\gradlew.bat assembleRelease` |

### 個別手順（手動）

```powershell
Set-Location "C:\システム開発\バンドナイフ張力測定\android"
.\gradlew.bat assembleRelease
Copy-Item -LiteralPath "app\build\outputs\apk\release\app-release.apk" `
  -Destination "..\バンドナイフ張力計.apk" -Force
.\install-apk.ps1
```

## パスと環境

| 項目 | 値 |
|------|-----|
| プロジェクトルート | `C:\システム開発\バンドナイフ張力測定` |
| ビルド成果物 | `android\app\build\outputs\apk\release\app-release.apk` |
| 配布用 APK | `バンドナイフ張力計.apk`（プロジェクトルート） |
| インストール用一時 APK | `%TEMP%\bandknife-tension-install.apk` |
| ADB | `C:\platform-tools-latest-windows\platform-tools\adb.exe` |
| Gradle ラッパー | `android/gradlew.bat` |

`JAVA_HOME` が JDK 22 以上でも、`gradlew.bat` が `resolve-java-home.bat` 経由で JDK 21 を自動選択する。**手動で JAVA_HOME を切り替えない。**

任意の JDK 固定:

- 環境変数 `GRADLE_JAVA_HOME`
- `android/local.properties` の `java.home`（例は `local.properties.example` 参照）

## デプロイ前チェック

1. USB デバッグ有効な Android 端末が接続されていること
2. `adb devices` で `device` 状態であること（`unauthorized` の場合は端末で許可）

```powershell
& "C:\platform-tools-latest-windows\platform-tools\adb.exe" devices
```

## トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| `filename doesn't end .apk` / パスが文字化け | `.bat` 内の日本語パス | `deploy.ps1` / `install-apk.ps1` を使う（`deploy.bat` 経由で可） |
| `Can't open file: -S` | `app-release.apk` を adb に直接指定 | `install-apk.ps1` を使う（一時ファイル経由） |
| `26` で Gradle が即失敗 | JDK 未検出 | `resolve-java-home.bat` / JDK 21 の有無を確認 |
| `adb not found` | ADB パス不一致 | `install-apk.ps1` の `$Adb` パスを確認 |
| `no devices` | 未接続・未許可 | USB・デバッグ許可を確認 |
| `INSTALL_FAILED` | 署名競合等 | 旧版をアンインストールするか `-r` で再インストール |
| ビルド成功だが変更が反映されない | 古い APK | `deploy.bat` で再ビルド。APK の更新日時を確認 |

**回避してはいけないこと:**

- `adb install` に `app-release.apk` や日本語ファイル名を直接渡す
- `.bat` に `バンドナイフ張力計.apk` などの日本語パスを埋め込む

## 完了報告

成功時は次を簡潔に伝える:

- ビルド結果（BUILD SUCCESSFUL）
- 配布用 APK パス（`バンドナイフ張力計.apk`）とサイズ
- 接続端末 ID
- インストール結果（Success / 失敗理由）

インストールのみ依頼された場合は `install-apk.bat` だけ実行してよい。
