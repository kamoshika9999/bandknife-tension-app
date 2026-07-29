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
const EQUIPMENT_NAME = 'equipment.json';
const EQUIPMENT_SHEET = '設備マスター';
const HISTORY_NAME = 'equipment-history.json';
const HISTORY_SHEET = '設備マスター履歴';
const USERS_NAME = 'users.json';
const USERS_SHEET = '使用者';

/** 履歴として残す世代数。古いものから捨てる。 */
const HISTORY_LIMIT = 100;

/** 設備の中身まで残す世代数。復元用なので直近だけでよい。 */
const SNAPSHOT_LIMIT = 10;

/**
 * 応答に載せる API 世代。
 * 端末はこの数値で「認証や世代管理に対応した版か」を判定する。
 * kind の有無だけでは、認証を持たない旧版を見分けられない。
 */
const API_VERSION = 2;

/**
 * 使用者を登録するときの合言葉。スクリプトのプロパティに REGISTRATION_KEY を
 * 設定すると必須になる。ウェブアプリ URL は APK から取り出せるため、
 * これを設定しない限り「URL を知る者は誰でも登録できる」状態である点に注意。
 */
function registrationKey() {
  return PropertiesService.getScriptProperties().getProperty('REGISTRATION_KEY') || '';
}

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const folder = getOrCreateFolder();
    // action 未指定は旧バージョンのアプリからの測定記録。互換のために残す。
    if (data.action == null || data.action === '' || data.action === 'appendRecord') {
      return jsonOutput(appendRecord(folder, data));
    }
    switch (data.action) {
      case 'pushEquipment':
        return jsonOutput(pushEquipment(folder, data));
      case 'registerUser':
        return jsonOutput(registerUser(folder, data));
      case 'verifyUser':
        return jsonOutput(verifyUserAction(folder, data));
      case 'updateUser':
        return jsonOutput(updateUser(folder, data));
      default:
        // 綴り違いや将来の action を測定記録として書き込むと記録が汚れる
        return jsonOutput({
          status: 'error',
          message: '未対応の操作です（' + data.action + '）。アプリを更新してください'
        });
    }
  } catch (err) {
    return jsonOutput({ status: 'error', message: err.message });
  }
}

function jsonOutput(payload) {
  payload.apiVersion = API_VERSION;
  return ContentService.createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * 測定記録の追記。CSV は全文を読んで書き戻すため、
 * ロックを取らないと同時送信で先の記録が消える。
 */
function appendRecord(folder, data) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    return { status: 'error', message: '他の端末が送信中です。しばらく待ってからやり直してください' };
  }
  try {
    const sheet = getOrCreateSpreadsheet(folder);
    appendToSheet(sheet, data);
    appendToCsv(folder, data);
    return { status: 'ok' };
  } finally {
    lock.releaseLock();
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

// ===== 設備マスター =====
// 端末側は equipment.json を唯一の正とする。ここに無い設備では測定できない。

const EQUIPMENT_FIELDS = [
  'uuid', 'name', 'massPerMeter', 'spanMeters', 'standardTension',
  'specLower', 'specUpper', 'useHzMode', 'standardHz', 'specHzLower',
  'specHzUpper', 'widthMm', 'thicknessMm', 'density',
  'youngModulusGpa', 'materialId', 'vibrationMode', 'edgewiseBending'
];

function pullEquipment(folder) {
  const master = readEquipmentMaster(folder);
  return equipmentResponse(master);
}

/**
 * kind を必ず添える。旧バージョンのスクリプトが残っている端末が
 * 「空の設備マスターを取り込めた」と誤解して測定を許してしまうのを防ぐ。
 */
function equipmentResponse(master) {
  return {
    status: 'ok',
    kind: 'equipment',
    revision: master.revision,
    updatedAt: master.updatedAt,
    updatedBy: master.updatedBy,
    equipment: master.equipment
  };
}

function pushEquipment(folder, data) {
  const incoming = data.equipment;
  if (!Array.isArray(incoming)) {
    return { status: 'error', message: 'equipment 配列がありません' };
  }
  // 空で置き換えると全端末が測定不能になる
  if (incoming.length === 0) {
    return { status: 'error', message: '設備が 1 件もありません。空のマスターには置き換えられません' };
  }
  // 全端末が同じファイルを書き換えるため、読み取りから書き込みまでを直列化する
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    return { status: 'error', message: '他の端末が更新中です。しばらく待ってからやり直してください' };
  }
  try {
    // 設備マスターは誰でも書き換えられてはいけない。登録済みの使用者だけに許す。
    const auth = authenticate(folder, data);
    if (!auth.ok) return authError(auth);

    const current = readEquipmentMaster(folder);
    const base = Number(data.baseRevision);
    if (!isFinite(base) || base < 0) {
      return {
        status: 'error',
        message: '設備マスターの版数が必要です。アプリを更新してから、もう一度登録してください'
      };
    }
    if (base !== current.revision) {
      return {
        status: 'error',
        message: '他の人が先に設備マスターを更新しました（第 ' + current.revision +
          ' 版）。「今すぐ確認」で取り込み直してから、もう一度登録してください'
      };
    }

    const normalized = [];
    for (let i = 0; i < incoming.length; i++) {
      const validated = validateEquipment(incoming[i], i);
      if (validated.error) return { status: 'error', message: validated.error };
      normalized.push(validated.value);
    }
    const duplicate = findDuplicateUuid(normalized);
    if (duplicate) {
      return { status: 'error', message: '設備の識別子が重複しています（' + duplicate + '）' };
    }

    const master = {
      revision: current.revision + 1,
      updatedAt: Date.now(),
      updatedBy: auth.user.name,
      equipment: normalized
    };
    const changes = describeChanges(current.equipment, master.equipment);
    // 消える設備がある登録は、端末側で内容を見せて確認を取ってからでないと通さない
    if (changes.removedCount > 0 && data.confirmRemoval !== true) {
      return {
        status: 'error',
        kind: 'confirmRemoval',
        message: 'この登録で ' + changes.removedCount + ' 件の設備が全端末から消えます（' +
          changes.removedNames.join('、') + '）。よければもう一度確認して実行してください',
        removedNames: changes.removedNames
      };
    }
    // 人が読むための写しが失敗しても、正である JSON の整合は保つ
    writeEquipmentMaster(folder, master);
    safely(function () { writeEquipmentSheet(folder, master); });
    safely(function () { appendHistory(folder, master, changes); });
    return equipmentResponse(master);
  } finally {
    lock.releaseLock();
  }
}

/** 写し（シートや履歴）の失敗で本処理を巻き戻さない。 */
function safely(fn) {
  try {
    fn();
  } catch (err) {
    console.error(err);
  }
}

/**
 * 規格値の妥当性を確かめる。
 * 欠損を 0 で埋めて通すと、下限も上限も 0 の設備ができて何を測っても合格になる。
 */
function validateEquipment(item, index) {
  const where = (index + 1) + ' 件目の設備';
  if (!item || typeof item !== 'object') return { error: where + 'の形式が正しくありません' };
  const uuid = String(item.uuid || '').trim();
  const name = String(item.name || '').trim();
  if (!uuid) return { error: where + 'に識別子がありません' };
  if (!name) return { error: where + 'に名前がありません' };

  const useHzMode = item.useHzMode === true;
  const value = {
    uuid: uuid,
    name: name,
    massPerMeter: Number(item.massPerMeter),
    spanMeters: Number(item.spanMeters),
    standardTension: Number(item.standardTension) || 0,
    specLower: Number(item.specLower) || 0,
    specUpper: Number(item.specUpper) || 0,
    useHzMode: useHzMode,
    standardHz: Number(item.standardHz) || 0,
    specHzLower: Number(item.specHzLower) || 0,
    specHzUpper: Number(item.specHzUpper) || 0,
    widthMm: Number(item.widthMm) || 0,
    thicknessMm: Number(item.thicknessMm) || 0,
    density: Number(item.density) || 7850,
    youngModulusGpa: Number(item.youngModulusGpa) || 210,
    materialId: String(item.materialId || 'carbon_steel'),
    vibrationMode: Math.max(1, Number(item.vibrationMode) || 1),
    edgewiseBending: item.edgewiseBending !== false
  };
  if (!(value.massPerMeter > 0)) return { error: '「' + name + '」の単位質量が 0 以下です' };
  if (!(value.spanMeters > 0)) return { error: '「' + name + '」のスパン長が 0 以下です' };
  if (useHzMode) {
    if (!(value.specHzLower > 0) || !(value.specHzUpper > value.specHzLower)) {
      return { error: '「' + name + '」の規格（Hz）が正しくありません' };
    }
  } else if (!(value.specLower > 0) || !(value.specUpper > value.specLower)) {
    return { error: '「' + name + '」の規格（張力）が正しくありません' };
  }
  return { value: value };
}

function findDuplicateUuid(list) {
  const seen = {};
  for (let i = 0; i < list.length; i++) {
    if (seen[list[i].uuid]) return list[i].name;
    seen[list[i].uuid] = true;
  }
  return null;
}

// ===== 世代管理 =====

/** 前回との差分を人が読める形にまとめる。監査で「何が変わったか」を追えるようにする。 */
function describeChanges(before, after) {
  const beforeMap = {};
  before.forEach(function (eq) { beforeMap[eq.uuid] = eq; });
  const afterMap = {};
  after.forEach(function (eq) { afterMap[eq.uuid] = eq; });

  const added = [];
  const removed = [];
  const changed = [];

  after.forEach(function (eq) {
    const prev = beforeMap[eq.uuid];
    if (!prev) { added.push(eq.name); return; }
    const diffs = EQUIPMENT_FIELDS.filter(function (key) {
      return key !== 'uuid' && String(prev[key]) !== String(eq[key]);
    });
    if (diffs.length > 0) {
      changed.push(eq.name + '（' + diffs.map(fieldLabel).join('・') + '）');
    }
  });
  before.forEach(function (eq) {
    if (!afterMap[eq.uuid]) removed.push(eq.name);
  });

  const parts = [];
  if (added.length) parts.push('追加: ' + added.join('、'));
  if (changed.length) parts.push('変更: ' + changed.join('、'));
  if (removed.length) parts.push('削除: ' + removed.join('、'));
  return {
    summary: parts.length ? parts.join(' / ') : '変更なし',
    addedCount: added.length,
    changedCount: changed.length,
    removedCount: removed.length,
    removedNames: removed
  };
}

function fieldLabel(key) {
  const labels = {
    name: '設備名', massPerMeter: '線密度', spanMeters: 'スパン',
    standardTension: '標準張力', specLower: '規格下限', specUpper: '規格上限',
    useHzMode: '管理方法', standardHz: '標準Hz', specHzLower: '規格下限Hz',
    specHzUpper: '規格上限Hz', widthMm: '幅', thicknessMm: '厚み', density: '密度'
  };
  return labels[key] || key;
}

function appendHistory(folder, master, changes) {
  const history = readHistory(folder);
  history.entries.unshift({
    revision: master.revision,
    updatedAt: master.updatedAt,
    updatedBy: master.updatedBy,
    summary: changes.summary,
    addedCount: changes.addedCount,
    changedCount: changes.changedCount,
    removedCount: changes.removedCount,
    equipmentCount: master.equipment.length,
    equipment: master.equipment
  });
  history.entries = history.entries.slice(0, HISTORY_LIMIT);
  // 全世代の設備を丸ごと持つとファイルが膨らみ、登録のたびに重くなる。
  // 復元に使うのは直近だけなので、古い世代は概要のみ残す。
  history.entries.forEach(function (entry, index) {
    if (index >= SNAPSHOT_LIMIT) delete entry.equipment;
  });
  writeJsonFile(folder, HISTORY_NAME, history);
  appendHistorySheet(folder, master, changes);
}

function readHistory(folder) {
  const parsed = readJsonFile(folder, HISTORY_NAME);
  return { entries: parsed && Array.isArray(parsed.entries) ? parsed.entries : [] };
}

function appendHistorySheet(folder, master, changes) {
  const ss = getOrCreateSpreadsheet(folder);
  let sheet = ss.getSheetByName(HISTORY_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(HISTORY_SHEET);
    sheet.appendRow(['日時', 'リビジョン', '更新者', '設備数', '追加', '変更', '削除', '変更内容']);
  }
  sheet.appendRow([
    formatTime(master.updatedAt),
    master.revision,
    master.updatedBy,
    master.equipment.length,
    changes.addedCount,
    changes.changedCount,
    changes.removedCount,
    changes.summary
  ]);
}

/** 端末に返す履歴。設備の中身まで返すと重いので概要だけにする。 */
function pullHistory(folder) {
  const history = readHistory(folder);
  return {
    status: 'ok',
    kind: 'history',
    entries: history.entries.slice(0, 30).map(function (e) {
      return {
        revision: e.revision,
        updatedAt: e.updatedAt,
        updatedBy: e.updatedBy,
        summary: e.summary,
        equipmentCount: e.equipmentCount != null
          ? e.equipmentCount
          : (Array.isArray(e.equipment) ? e.equipment.length : 0)
      };
    })
  };
}

// ===== 使用者管理 =====
// 端末はパスワードそのものを送らない。端末側で PBKDF2 により導出した secret を送り、
// ここではさらに salt 付きでハッシュ化して保存する。

function registerUser(folder, data) {
  const name = displayName(data.name);
  const key = nameKey(data.name);
  const secret = String(data.secret || '');
  if (!name) return { status: 'error', message: '名前を入力してください' };
  if (!secret) return { status: 'error', message: 'パスワードを入力してください' };

  const required = registrationKey();
  if (required && String(data.registrationKey || '') !== required) {
    return {
      status: 'error',
      message: '登録キーが違います。管理者から渡された登録キーを入力してください'
    };
  }

  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    return { status: 'error', message: '他の端末が更新中です。しばらく待ってからやり直してください' };
  }
  try {
    const store = readUsers(folder);
    if (findUser(store, key)) {
      return {
        status: 'error',
        message: '「' + name + '」は既に登録されています。同じ人なら「登録済みの名前」を選んでください'
      };
    }
    const now = Date.now();
    const salt = Utilities.getUuid();
    store.users.push({
      key: key,
      name: name,
      salt: salt,
      hash: sha256Hex(salt + secret),
      createdAt: now,
      updatedAt: now
    });
    writeUsers(folder, store);
    return { status: 'ok', kind: 'user', name: name };
  } finally {
    lock.releaseLock();
  }
}

/** authenticate の失敗をクライアントが code で判別できる形にする。 */
function authError(auth) {
  var out = { status: 'error', message: auth.message };
  if (auth.code) out.code = auth.code;
  return out;
}

function verifyUserAction(folder, data) {
  const auth = authenticate(folder, data);
  if (!auth.ok) return authError(auth);
  return { status: 'ok', kind: 'user', name: auth.user.name };
}

function updateUser(folder, data) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    return { status: 'error', message: '他の端末が更新中です。しばらく待ってからやり直してください' };
  }
  try {
    const auth = authenticate(folder, data);
    if (!auth.ok) return authError(auth);

    const store = auth.store;
    const user = auth.user;
    const newName = displayName(data.newName);
    const newKey = nameKey(data.newName);
    const newSecret = String(data.newSecret || '');

    if (newName && newKey !== user.key) {
      if (findUser(store, newKey)) {
        return { status: 'error', message: '「' + newName + '」は既に使われています。別の名前にしてください' };
      }
      user.key = newKey;
      user.name = newName;
    } else if (newName) {
      user.name = newName;
    }
    if (newSecret) {
      user.salt = Utilities.getUuid();
      user.hash = sha256Hex(user.salt + newSecret);
    }
    user.updatedAt = Date.now();
    writeUsers(folder, store);
    return { status: 'ok', kind: 'user', name: user.name };
  } finally {
    lock.releaseLock();
  }
}

function authenticate(folder, data) {
  const key = nameKey(data.name);
  const secret = String(data.secret || '');
  if (!key || !secret) {
    return { ok: false, message: 'この操作には名前とパスワードが必要です' };
  }
  const store = readUsers(folder);
  const user = findUser(store, key);
  if (!user) {
    return {
      ok: false,
      code: 'UNKNOWN_USER',
      message: '「' + displayName(data.name) + '」は登録されていません。' +
        '設定画面の「使用者」から登録し直してください'
    };
  }
  if (sha256Hex(user.salt + secret) !== user.hash) {
    return {
      ok: false,
      code: 'BAD_PASSWORD',
      message: 'パスワードが違います。別の端末で変更した場合は、新しいパスワードで設定し直してください'
    };
  }
  return { ok: true, store: store, user: user };
}

function findUser(store, key) {
  for (let i = 0; i < store.users.length; i++) {
    const user = store.users[i];
    // key を持たない旧データは名前から補う
    if (!user.key) user.key = nameKey(user.name);
    if (user.key === key) return user;
  }
  return null;
}

/** 表示に使う名前。前後の空白だけ落とす。 */
function displayName(value) {
  return String(value == null ? '' : value).trim();
}

/**
 * 照合に使うキー。端末側の PasswordHasher と同じ規則にする。
 * ここがずれると、大文字小文字違いの同名で secret を共有できてしまう。
 */
function nameKey(value) {
  return displayName(value).normalize('NFKC').toLowerCase();
}

function readUsers(folder) {
  const parsed = readJsonFile(folder, USERS_NAME);
  return { users: parsed && Array.isArray(parsed.users) ? parsed.users : [] };
}

function writeUsers(folder, store) {
  writeJsonFile(folder, USERS_NAME, store);
  // 人が読むための写し。ここで失敗しても認証情報の整合は崩さない。
  safely(function () { writeUsersSheet(folder, store); });
}

/** 誰が登録されているかを人が確認できるようにする。パスワードは書かない。 */
function writeUsersSheet(folder, store) {
  const ss = getOrCreateSpreadsheet(folder);
  let sheet = ss.getSheetByName(USERS_SHEET);
  if (!sheet) sheet = ss.insertSheet(USERS_SHEET);
  sheet.clear();
  sheet.appendRow(['名前', '登録日時', '最終更新日時']);
  store.users.forEach(function (u) {
    sheet.appendRow([u.name, formatTime(u.createdAt), formatTime(u.updatedAt)]);
  });
}

/** 日時が壊れていても書き込み全体を落とさない。 */
function formatTime(millis) {
  const value = Number(millis);
  if (!isFinite(value) || value <= 0) return '';
  return Utilities.formatDate(new Date(value), 'Asia/Tokyo', 'yyyy/MM/dd HH:mm:ss');
}

function sha256Hex(text) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8);
  return bytes.map(function (b) {
    return ('0' + (b & 0xFF).toString(16)).slice(-2);
  }).join('');
}

// ===== JSON ファイルの読み書き =====

/**
 * ファイルが無い場合は null。
 * ファイルはあるが壊れている場合は例外にする。空として扱うと、
 * 設備マスターが消えたのと同じ結果を全端末に配ってしまう。
 */
function readJsonFile(folder, fileName) {
  const files = folder.getFilesByName(fileName);
  if (!files.hasNext()) return null;
  const file = files.next();
  try {
    return JSON.parse(file.getBlob().getDataAsString('UTF-8'));
  } catch (err) {
    throw new Error(
      fileName + ' が壊れています。管理者に連絡してください（ドライブの「' +
        FOLDER_NAME + '」フォルダを確認）'
    );
  }
}

function writeJsonFile(folder, fileName, value) {
  const content = JSON.stringify(value, null, 2);
  const files = folder.getFilesByName(fileName);
  if (files.hasNext()) files.next().setContent(content);
  else folder.createFile(fileName, content, MimeType.PLAIN_TEXT);
}

function normalizeEquipment(item) {
  const out = {};
  EQUIPMENT_FIELDS.forEach(function (key) {
    const value = item[key];
    if (key === 'uuid' || key === 'name') out[key] = String(value || '');
    else if (key === 'useHzMode') out[key] = value === true;
    else out[key] = Number(value) || 0;
  });
  if (!out.uuid) out.uuid = Utilities.getUuid();
  return out;
}

function readEquipmentMaster(folder) {
  const parsed = readJsonFile(folder, EQUIPMENT_NAME);
  if (!parsed) return { revision: 0, updatedAt: 0, updatedBy: '', equipment: [] };
  return {
    revision: Number(parsed.revision) || 0,
    updatedAt: Number(parsed.updatedAt) || 0,
    updatedBy: String(parsed.updatedBy || ''),
    equipment: Array.isArray(parsed.equipment) ? parsed.equipment.map(normalizeEquipment) : []
  };
}

function writeEquipmentMaster(folder, master) {
  writeJsonFile(folder, EQUIPMENT_NAME, master);
}

/** 人が中身を確認・監査できるよう、スプレッドシートにも写しを残す。 */
function writeEquipmentSheet(folder, master) {
  const ss = getOrCreateSpreadsheet(folder);
  let sheet = ss.getSheetByName(EQUIPMENT_SHEET);
  if (!sheet) sheet = ss.insertSheet(EQUIPMENT_SHEET);
  sheet.clear();
  sheet.appendRow([
    '設備名', 'Hz管理', '標準張力(N)', '規格下限(N)', '規格上限(N)',
    '標準(Hz)', '規格下限(Hz)', '規格上限(Hz)',
    '線密度(kg/m)', 'スパン(m)', '幅(mm)', '厚み(mm)', '密度(kg/m3)', 'ID'
  ]);
  master.equipment.forEach(function (eq) {
    sheet.appendRow([
      eq.name, eq.useHzMode ? 'Hz' : 'N', eq.standardTension, eq.specLower, eq.specUpper,
      eq.standardHz, eq.specHzLower, eq.specHzUpper,
      eq.massPerMeter, eq.spanMeters, eq.widthMm, eq.thicknessMm, eq.density, eq.uuid
    ]);
  });
  sheet.appendRow([]);
  sheet.appendRow([
    '更新日時', formatTime(master.updatedAt),
    'リビジョン', master.revision, '更新者', master.updatedBy
  ]);
}

/**
 * 設備マスターの取得は GET で受ける。
 * 旧バージョンのスクリプトが残っていても、GET なら測定記録に空行が書き込まれない。
 */
function doGet(e) {
  const action = e && e.parameter ? e.parameter.action : '';
  if (action !== 'pullEquipment' && action !== 'pullHistory') {
    return ContentService.createTextOutput('バンドナイフ張力測定 API - POSTでデータを送信してください');
  }
  try {
    const folder = getOrCreateFolder();
    return jsonOutput(action === 'pullHistory' ? pullHistory(folder) : pullEquipment(folder));
  } catch (err) {
    return jsonOutput({ status: 'error', message: err.message });
  }
}
