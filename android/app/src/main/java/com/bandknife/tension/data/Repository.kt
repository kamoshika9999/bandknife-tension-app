package com.bandknife.tension.data

import android.content.Context
import androidx.room.withTransaction
import com.bandknife.tension.domain.PasswordHasher
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.upload.DriveSyncClient
import com.bandknife.tension.upload.DriveUploader
import com.bandknife.tension.upload.HistoryOutcome
import com.bandknife.tension.upload.QueueUploadResult
import com.bandknife.tension.upload.SharedFolderSync
import com.bandknife.tension.upload.SyncOutcome
import com.bandknife.tension.upload.UploadResult
import com.bandknife.tension.upload.UserOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 設備マスター同期の結果。UI にそのまま出せる文言を持たせる。 */
data class EquipmentSyncReport(
    val success: Boolean,
    val message: String,
    val syncedCount: Int = 0,
    val droppedCount: Int = 0,
    /** 設備が消える登録だったため保留した。内容を見せて確認を取り直す。 */
    val needsRemovalConfirmation: Boolean = false,
    val removedNames: List<String> = emptyList()
)

/**
 * 使用者の登録・ログイン・変更の結果。
 * @param staleCredential 端末の資格情報がドライブ側と食い違っている。再設定が要る。
 */
data class UserActionReport(
    val success: Boolean,
    val message: String,
    val staleCredential: Boolean = false
)

class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val equipmentDao = db.equipmentDao()
    private val recordDao = db.recordDao()
    private val uploadQueueDao = db.uploadQueueDao()
    private val prefs = PreferencesManager(context)
    private val uploader = DriveUploader(context, uploadQueueDao)
    private val syncClient = DriveSyncClient(context)
    private val sharedFolderSync = SharedFolderSync()
    private val credentialStore = UserCredentialStore(context)
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadQueueMutex = Mutex()

    init {
        uploadScope.launch { runStartupSharedFolderSync() }
    }

    val equipment: Flow<List<EquipmentEntity>> = equipmentDao.observeAll()
    val deletedEquipment: Flow<List<EquipmentEntity>> = equipmentDao.observeDeleted()
    val records: Flow<List<MeasurementRecordEntity>> = recordDao.observeAll()
    val preferences = prefs

    /** サインイン中の使用者。null なら設備の追加・変更ができない。 */
    val credential: StateFlow<UserCredential?> = credentialStore.credential

    suspend fun getEquipment(id: Long) = equipmentDao.getById(id)

    /**
     * 端末で設備を作成・編集しても、ドライブへ登録するまでは測定に使えない。
     * 規格値を勝手に書き換えた端末だけで測ってしまう事故を防ぐため、保存時に必ず未同期へ落とす。
     */
    suspend fun saveEquipment(entity: EquipmentEntity) =
        equipmentDao.insert(entity.withLocalEdit())

    suspend fun updateEquipment(entity: EquipmentEntity) =
        equipmentDao.update(entity.withLocalEdit())

    private fun EquipmentEntity.withLocalEdit() = copy(
        // 新規行は常に新しい UUID。複製で元の uuid が残ると同期が食い違う。
        uuid = if (id == 0L) UUID.randomUUID().toString()
        else uuid.ifBlank { UUID.randomUUID().toString() },
        syncedAt = null
    )

    suspend fun ensureDefaultEquipmentIfNeeded() {
        if (equipmentDao.countActive() > 0) return
        saveEquipment(
            EquipmentEntity(
                name = "ペフ用スライサー1号",
                massPerMeter = 0.844,
                spanMeters = TensionCalculator.DEFAULT_SPAN_METERS,
                standardTension = 160.0,
                specLower = 150.0,
                specUpper = 180.0,
                widthMm = 86.0,
                thicknessMm = 1.25
            )
        )
    }

    suspend fun deleteEquipment(entity: EquipmentEntity) =
        equipmentDao.softDelete(entity.id, System.currentTimeMillis())
    suspend fun restoreEquipment(entity: EquipmentEntity) = equipmentDao.restore(entity.id)
    suspend fun permanentlyDeleteEquipment(entity: EquipmentEntity) = equipmentDao.permanentlyDelete(entity.id)

    suspend fun saveRecord(record: MeasurementRecordEntity): Long {
        val localMode = prefs.localMode.first()
        val toSave = if (localMode) record.copy(localOnly = true) else record
        val id = recordDao.insert(toSave)
        val saved = toSave.copy(id = id)
        if (!localMode && !saved.localOnly) {
            val queued = uploader.enqueueIfEnabled(saved)
            if (queued) uploadScope.launch { runCatching { processUploadQueue() } }
        }
        return id
    }

    suspend fun updateRecord(record: MeasurementRecordEntity) = recordDao.update(record)

    suspend fun deleteRecord(record: MeasurementRecordEntity) {
        if (!record.isLocked) recordDao.delete(record)
    }

    suspend fun deleteRecordsInRange(from: Long, to: Long) = recordDao.deleteInRange(from, to)

    fun recordsForEquipment(equipmentId: Long): Flow<List<MeasurementRecordEntity>> =
        recordDao.observeByEquipment(equipmentId)

    suspend fun exportBackup(): String {
        val json = JSONObject()
        val eqArray = JSONArray()
        equipmentDao.getAll().forEach { eq ->
            eqArray.put(JSONObject().apply {
                put("name", eq.name)
                put("massPerMeter", eq.massPerMeter)
                put("spanMeters", eq.spanMeters)
                put("standardTension", eq.standardTension)
                put("specLower", eq.specLower)
                put("specUpper", eq.specUpper)
                put("useHzMode", eq.useHzMode)
                put("standardHz", eq.standardHz)
                put("specHzLower", eq.specHzLower)
                put("specHzUpper", eq.specHzUpper)
            })
        }
        json.put("equipment", eqArray)
        return json.toString(2)
    }

    // ===== 設備マスターの同期 =====

    /**
     * ドライブの設備マスターを取り込む。ドライブが正なので、端末の内容は上書きする。
     * ドライブに無い設備は行を残したまま測定不可に落とす。過去の履歴が設備名を参照しているため、
     * ここで物理削除すると履歴が読めなくなる。
     */
    suspend fun pullEquipmentFromDrive(): EquipmentSyncReport {
        if (prefs.localMode.first()) {
            return EquipmentSyncReport(false, "ローカルモード中はドライブと同期しません")
        }
        return applyOutcome(syncClient.pull())
    }

    /**
     * 端末の設備一覧をドライブへ登録し、返ってきたマスターをそのまま取り込む。
     *
     * @param confirmRemoval 設備が消えることを利用者が承知しているか。
     *   最初の呼び出しは false で送り、消える設備があればその内容を返させる。
     */
    suspend fun pushEquipmentToDrive(confirmRemoval: Boolean = false): EquipmentSyncReport {
        if (prefs.localMode.first()) {
            return EquipmentSyncReport(false, "ローカルモード中はドライブへ登録できません")
        }
        val user = credentialStore.current
            ?: return EquipmentSyncReport(false, NOT_SIGNED_IN_MESSAGE)
        // getAll は削除済みを含まない。ドライブから消すことが「削除」になる。
        val local = equipmentDao.getAll().map { eq ->
            if (eq.uuid.isBlank()) {
                val withUuid = eq.copy(uuid = UUID.randomUUID().toString())
                equipmentDao.update(withUuid)
                withUuid
            } else {
                eq
            }
        }
        if (local.isEmpty()) {
            return EquipmentSyncReport(false, "登録できる設備がありません。先に設備を追加してください")
        }
        local.firstOrNull { !it.hasUsableSpec }?.let {
            return EquipmentSyncReport(
                false,
                "「${it.name}」の規格値が未設定です。規格の下限と上限を入れてから登録してください"
            )
        }
        return applyOutcome(
            syncClient.push(
                equipment = local,
                name = user.name,
                secret = user.secret,
                baseRevision = prefs.equipmentRevision.first(),
                confirmRemoval = confirmRemoval
            )
        )
    }

    suspend fun pullEquipmentHistory(): HistoryOutcome {
        if (prefs.localMode.first()) {
            return HistoryOutcome.Failure("ローカルモード中は更新履歴を取得できません")
        }
        return syncClient.pullHistory()
    }

    // ===== 使用者 =====

    /** 新しい使用者をドライブに登録し、この端末にサインインした状態にする。 */
    suspend fun registerUser(
        name: String,
        password: String,
        registrationKey: String = ""
    ): UserActionReport {
        val trimmed = name.trim()
        val secret = derive(trimmed, password)
        return when (val outcome = syncClient.registerUser(trimmed, secret, registrationKey.trim())) {
            is UserOutcome.Failure -> UserActionReport(false, outcome.message)
            is UserOutcome.Success -> {
                val saved = outcome.name.ifBlank { trimmed }
                persist(UserCredential(saved, secret), "使用者「$saved」を登録しました")
            }
        }
    }

    /** 登録済みの使用者としてこの端末にサインインする。 */
    suspend fun signIn(name: String, password: String): UserActionReport {
        val trimmed = name.trim()
        val secret = derive(trimmed, password)
        return when (val outcome = syncClient.verifyUser(trimmed, secret)) {
            is UserOutcome.Failure -> UserActionReport(false, outcome.message)
            is UserOutcome.Success -> {
                val saved = outcome.name.ifBlank { trimmed }
                persist(UserCredential(saved, secret), "「$saved」として設定しました")
            }
        }
    }

    /**
     * 端末に保存された資格情報がまだ有効か確かめる。
     * 別の端末で名前やパスワードを変えられていると、この端末は変更前のまま残る。
     */
    suspend fun verifyStoredCredential(): UserActionReport? {
        if (prefs.localMode.first()) return null
        val user = credentialStore.current ?: return null
        return when (val outcome = syncClient.verifyUser(user.name, user.secret)) {
            is UserOutcome.Success -> UserActionReport(true, "")
            is UserOutcome.Failure -> {
                if (outcome.staleCredential) credentialStore.clear()
                UserActionReport(false, outcome.message, staleCredential = outcome.staleCredential)
            }
        }
    }

    private suspend fun persist(credential: UserCredential, message: String): UserActionReport =
        if (credentialStore.save(credential)) {
            UserActionReport(true, message)
        } else {
            UserActionReport(false, SAVE_FAILED_MESSAGE)
        }

    /**
     * 名前やパスワードを変える。
     * 名前は塩に混ぜているため、名前を変えると secret も導出し直す必要がある。
     */
    suspend fun updateUser(
        currentPassword: String,
        newName: String?,
        newPassword: String?
    ): UserActionReport {
        val user = credentialStore.current ?: return UserActionReport(false, NOT_SIGNED_IN_MESSAGE)
        val verifySecret = derive(user.name, currentPassword)
        if (verifySecret != user.secret) {
            return UserActionReport(false, "現在のパスワードが違います")
        }
        val nextName = newName?.trim()?.takeIf { it.isNotEmpty() && it != user.name }
        val nextPassword = newPassword?.takeIf { it.isNotEmpty() }
        if (nextName == null && nextPassword == null) {
            return UserActionReport(false, "変更する内容がありません")
        }
        val effectiveName = nextName ?: user.name
        val effectivePassword = nextPassword ?: currentPassword
        val nextSecret = derive(effectiveName, effectivePassword)

        if (user.localOnly) {
            return persist(
                UserCredential(effectiveName, nextSecret, localOnly = true),
                if (nextName != null) "「$effectiveName」に変更しました"
                else "パスワードを変更しました"
            )
        }

        return when (val outcome = syncClient.updateUser(user.name, user.secret, nextName, nextSecret)) {
            is UserOutcome.Failure -> UserActionReport(false, outcome.message)
            is UserOutcome.Success -> persist(
                UserCredential(effectiveName, nextSecret),
                if (nextName != null) {
                    "「$effectiveName」に変更しました。他の端末では設定し直してください"
                } else {
                    "パスワードを変更しました。他の端末では設定し直してください"
                }
            )
        }
    }

    suspend fun signOut() = credentialStore.clear()

    private suspend fun derive(name: String, password: String): String =
        withContext(Dispatchers.Default) { PasswordHasher.derive(name, password) }

    private suspend fun applyOutcome(outcome: SyncOutcome): EquipmentSyncReport = when (outcome) {
        is SyncOutcome.Failure -> EquipmentSyncReport(
            success = false,
            message = outcome.message,
            needsRemovalConfirmation = outcome.needsRemovalConfirmation,
            removedNames = outcome.removedNames
        )
        is SyncOutcome.Success -> {
            val now = System.currentTimeMillis()
            val master = outcome.master
            val knownRevision = prefs.equipmentRevision.first()
            if (master.revision < knownRevision) {
                // 世代が巻き戻る応答は、ドライブ側のファイルが失われた疑いがある。
                // そのまま取り込むと全端末の規格値が古い内容に戻る。
                EquipmentSyncReport(
                    false,
                    "ドライブの設備マスターが第 ${master.revision} 版に戻っています" +
                        "（この端末は第 ${knownRevision} 版）。取り込みを中止しました。管理者に連絡してください"
                )
            } else {
                // 途中で落ちて新旧の規格値が混ざらないよう、まとめて反映する
                db.withTransaction {
                    master.equipment.forEach { remote ->
                        val local = equipmentDao.getByUuid(remote.uuid)
                        if (local == null) {
                            equipmentDao.insert(remote.copy(syncedAt = now))
                        } else {
                            equipmentDao.update(
                                remote.copy(
                                    id = local.id,
                                    syncedAt = now,
                                    deleted = false,
                                    deletedAt = null
                                )
                            )
                        }
                    }
                    val uuids = master.equipment.map { it.uuid }
                    if (uuids.isEmpty()) equipmentDao.markAllUnsynced()
                    else equipmentDao.markUnsyncedExcept(uuids)
                }
                prefs.setEquipmentSyncedAt(now)
                prefs.setEquipmentRevision(master.revision)

                val dropped = equipmentDao.getAll().count { !it.isSynced }
                val notes = buildList {
                    if (dropped > 0) add("ドライブ未登録のため使えない設備が $dropped 件あります")
                    if (master.skippedCount > 0) {
                        add("規格値が未設定で取り込めない設備が ${master.skippedCount} 件あります。管理者に連絡してください")
                    }
                }
                EquipmentSyncReport(
                    success = true,
                    message = when {
                        master.equipment.isEmpty() && master.skippedCount == 0 ->
                            "ドライブに設備マスターがまだありません。管理者の端末で「ドライブへ登録」を実行してください"
                        notes.isEmpty() -> "設備 ${master.equipment.size} 件を取り込みました（第 ${master.revision} 版）"
                        else -> "設備 ${master.equipment.size} 件を取り込みました（${notes.joinToString("、")}）"
                    },
                    syncedCount = master.equipment.size,
                    droppedCount = dropped
                ).also {
                    uploadScope.launch {
                        runCatching { sharedFolderSync.mirrorEquipment(master) }
                    }
                }
            }
        }
    }


    suspend fun isLocalMode(): Boolean = prefs.localMode.first()

    /** 端末だけの使用者を登録する。ドライブが使えないときの設備変更用。 */
    suspend fun registerLocalUser(name: String, password: String): UserActionReport {
        val trimmed = name.trim()
        val secret = derive(trimmed, password)
        return persist(
            UserCredential(trimmed, secret, localOnly = true),
            "この端末の使用者「$trimmed」を設定しました"
        )
    }

    /** モード切替など、保存済みのパスワードが正しいか確かめる。 */
    suspend fun verifyPassword(password: String): Boolean {
        val user = credentialStore.current ?: return false
        return derive(user.name, password) == user.secret
    }

    suspend fun setLocalMode(enabled: Boolean) {
        prefs.setLocalMode(enabled)
    }

    suspend fun processUploadQueue(): QueueUploadResult = uploadQueueMutex.withLock {
        if (prefs.localMode.first()) {
            return QueueUploadResult(0, uploadQueueDao.count(), "ローカルモード中はアップロードしません")
        }
        val url = prefs.driveUrl.first()
        if (url.isBlank()) {
            return QueueUploadResult(0, uploadQueueDao.count(), "アップロード先 URL が未設定です。設定画面で URL を入力してください")
        }
        val items = uploadQueueDao.getAll()
        if (items.isEmpty()) {
            return QueueUploadResult(0, 0, "未送信のデータはありません")
        }
        var sent = 0
        var lastError = "送信に失敗しました"
        for (item in items) {
            val result = uploader.postForQueue(url, item.payloadJson)
            if (result.success) {
                uploadQueueDao.delete(item.id)
                sent++
                uploadScope.launch {
                    runCatching { mirrorUploadedPayload(item.payloadJson) }
                }
            } else {
                lastError = result.message
            }
        }
        val remaining = uploadQueueDao.count()
        val message = when {
            sent > 0 && remaining == 0 -> "${sent}件を送信しました"
            sent > 0 -> "${sent}件を送信しました（未送信: ${remaining}件）"
            else -> lastError
        }
        return QueueUploadResult(sent, remaining, message)
    }

    private suspend fun mirrorUploadedPayload(payloadJson: String) {
        val json = JSONObject(payloadJson)
        sharedFolderSync.mirrorRecord(
            MeasurementRecordEntity(
                equipmentId = 0L,
                timestamp = json.optLong("timestamp"),
                equipmentName = json.optString("equipmentName"),
                frequencyHz = json.optDouble("frequencyHz"),
                tensionN = json.optDouble("tensionN"),
                passed = json.optBoolean("passed"),
                sampleCount = json.optInt("sampleCount", 1),
                stdDev = json.optDouble("stdDev"),
                ci95Lower = json.optDouble("ci95Lower"),
                ci95Upper = json.optDouble("ci95Upper"),
                comment = json.optString("comment")
            )
        )
    }

    private suspend fun runStartupSharedFolderSync() {
        if (prefs.localMode.first()) return
        val pending = uploadQueueDao.getAll().mapNotNull { item ->
            JSONObject(item.payloadJson).optLong("timestamp").takeIf { it > 0L }
        }.toSet()
        val records = recordDao.getAll()
        val revision = prefs.equipmentRevision.first()
        if (revision <= 0) {
            sharedFolderSync.startupSync(records, null, pending)
            return
        }
        val master = com.bandknife.tension.upload.EquipmentMaster(
            revision = revision,
            updatedAt = prefs.equipmentSyncedAt.first(),
            updatedBy = "",
            equipment = equipmentDao.getAll()
        )
        sharedFolderSync.startupSync(records, master, pending)
    }

    suspend fun testUpload(): UploadResult {
        if (prefs.localMode.first()) {
            return UploadResult(false, "ローカルモード中はアップロードできません")
        }
        return uploader.testUpload()
    }

    suspend fun pendingUploadCount() = uploadQueueDao.count()

    fun evaluate(equipment: EquipmentEntity, frequencyHz: Double, tensionN: Double): Boolean {
        return if (equipment.useHzMode) {
            frequencyHz in equipment.specHzLower..equipment.specHzUpper
        } else {
            TensionCalculator.isWithinSpec(tensionN, equipment.specLower, equipment.specUpper)
        }
    }

    fun tensionFor(equipment: EquipmentEntity, frequencyHz: Double): Double =
        TensionCalculator.tensionFromEquipment(equipment, frequencyHz)

    private companion object {
        const val NOT_SIGNED_IN_MESSAGE =
            "使用者が設定されていません。「その他」の使用者欄で名前とパスワードを登録してください"
        const val SAVE_FAILED_MESSAGE =
            "この端末に使用者情報を保存できませんでした。端末を再起動してもう一度お試しください"
    }
}
