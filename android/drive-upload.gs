/**
 * バンドナイフ張力測定 - Googleドライブ連携スクリプト
 * 
 * セットアップ手順:
 * 1. https://script.google.com にアクセス
 * 2. 新しいプロジェクトを作成し、このコードを貼り付け
 * 3. 「デプロイ」→「新しいデプロイ」→種類「ウェブアプリ」
 * 4. アクセス: 「全員」、実行ユーザー: 「自分」
 * 5. デプロイ後のURLをアプリの設定画面に入力
 */

const FOLDER_NAME = 'バンドナイフ張力測定';
const SPREADSHEET_NAME = '張力記録';
const CSV_NAME = 'records.csv';

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const folder = getOrCreateFolder();
    const sheet = getOrCreateSpreadsheet(folder);
    appendToSheet(sheet, data);
    appendToCsv(folder, data);
    return ContentService.createTextOutput(JSON.stringify({ status: 'ok' }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: err.message }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function getOrCreateFolder() {
  const folders = DriveApp.getFoldersByName(FOLDER_NAME);
  if (folders.hasNext()) return folders.next();
  return DriveApp.createFolder(FOLDER_NAME);
}

function getOrCreateSpreadsheet(folder) {
  const files = folder.getFilesByName(SPREADSHEET_NAME);
  if (files.hasNext()) {
    return SpreadsheetApp.open(files.next());
  }
  const ss = SpreadsheetApp.create(SPREADSHEET_NAME);
  const file = DriveApp.getFileById(ss.getId());
  folder.addFile(file);
  DriveApp.getRootFolder().removeFile(file);
  const sheet = ss.getActiveSheet();
  sheet.setName('全記録');
  sheet.appendRow(['日時', '設備名', '周波数(Hz)', '張力(N)', '合否', 'n数', '標準偏差', 'CI下限', 'CI上限', 'コメント']);
  return ss;
}

function appendToSheet(ss, data) {
  const sheet = ss.getSheetByName('全記録') || ss.getActiveSheet();
  const date = new Date(data.timestamp);
  sheet.appendRow([
    Utilities.formatDate(date, 'Asia/Tokyo', 'yyyy/MM/dd HH:mm:ss'),
    data.equipmentName || '',
    data.frequencyHz || 0,
    data.tensionN || 0,
    data.passed ? 'OK' : '要調整',
    data.sampleCount || 1,
    data.stdDev || 0,
    data.ci95Lower || 0,
    data.ci95Upper || 0,
    data.comment || ''
  ]);
}

function appendToCsv(folder, data) {
  const header = 'timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n';
  const row = [
    data.timestamp, data.equipmentName, data.frequencyHz, data.tensionN,
    data.passed, data.sampleCount, data.stdDev, data.ci95Lower, data.ci95Upper,
    '"' + (data.comment || '').replace(/"/g, '""') + '"'
  ].join(',') + '\n';

  const files = folder.getFilesByName(CSV_NAME);
  if (files.hasNext()) {
    const file = files.next();
    const content = file.getBlob().getDataAsString('UTF-8');
    file.setContent(content + row);
  } else {
    folder.createFile(CSV_NAME, header + row, MimeType.PLAIN_TEXT);
  }
}

function doGet() {
  return ContentService.createTextOutput('バンドナイフ張力測定 API - POSTでデータを送信してください');
}
