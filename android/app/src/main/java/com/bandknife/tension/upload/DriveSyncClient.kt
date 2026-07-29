package com.bandknife.tension.upload

import android.content.Context
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.PreferencesManager
import com.bandknife.tension.domain.BladeMaterialPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** ドライブ上の設備マスターのスナップショット。 */
data class EquipmentMaster(
    val revision: Int,
    val updatedAt: Long,
    val updatedBy: String,
    val equipment: List<EquipmentEntity>,
    /** 規格値などが欠けていて取り込めなかった件数。黙って捨てると理由が分からなくなる。 */
    val skippedCount: Int = 0
)

/** 設備マスターの 1 世代。誰がいつ何を変えたかを表す。 */
data class EquipmentRevision(
    val revision: Int,
    val updatedAt: Long,
    val updatedBy: String,
    val summary: String,
    val equipmentCount: Int
)

sealed interface SyncOutcome {
    data class Success(val master: EquipmentMaster) : SyncOutcome

    /**
     * @param needsRemovalConfirmation 設備が消える登録なので、内容を見せてからやり直す必要がある
     */
    data class Failure(
        val message: String,
        val needsRemovalConfirmation: Boolean = false,
        val removedNames: List<String> = emptyList()
    ) : SyncOutcome
}

sealed interface UserOutcome {
    data class Success(val name: String) : UserOutcome

    /** @param staleCredential 端末に保存された資格情報がドライブ側と食い違っている */
    data class Failure(val message: String, val staleCredential: Boolean = false) : UserOutcome
}

sealed interface HistoryOutcome {
    data class Success(val revisions: List<EquipmentRevision>) : HistoryOutcome
    data class Failure(val message: String) : HistoryOutcome
}

/**
 * 設備マスターと使用者をドライブと同期する。
 * 測定の可否がこの通信結果に懸かるため、失敗理由は現場で次の一手が分かる文言にする。
 */
class DriveSyncClient(context: Context) {
    private val prefs = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** 対応済みのスクリプトだと分かったら、以降は往復を省く。 */
    @Volatile
    private var supportChecked = false

    // ===== 設備マスター =====

    /**
     * 取得は GET で行う。旧バージョンのスクリプトが残っている環境で POST すると、
     * action が無視されて測定記録に空行が書き込まれてしまう。
     */
    suspend fun pull(): SyncOutcome = when (val raw = get("pullEquipment", "equipment")) {
        is Raw.Err -> SyncOutcome.Failure(raw.message)
        is Raw.Ok -> SyncOutcome.Success(parseMaster(raw.json))
    }

    /**
     * @param baseRevision 取り込み済みの世代。ドライブ側が進んでいたら拒否させ、
     *   他の端末が追加した設備を消してしまうのを防ぐ。
     */
    suspend fun push(
        equipment: List<EquipmentEntity>,
        name: String,
        secret: String,
        baseRevision: Int,
        confirmRemoval: Boolean
    ): SyncOutcome {
        val array = JSONArray()
        equipment.forEach { array.put(toJson(it)) }
        val payload = JSONObject()
            .put("action", "pushEquipment")
            .put("name", name)
            .put("secret", secret)
            .put("baseRevision", baseRevision)
            .put("confirmRemoval", confirmRemoval)
            .put("equipment", array)
        return when (val raw = post(payload, "equipment")) {
            is Raw.Err -> SyncOutcome.Failure(
                message = raw.message,
                needsRemovalConfirmation = raw.kind == "confirmRemoval",
                removedNames = raw.details
            )
            is Raw.Ok -> SyncOutcome.Success(parseMaster(raw.json))
        }
    }

    suspend fun pullHistory(): HistoryOutcome = when (val raw = get("pullHistory", "history")) {
        is Raw.Err -> HistoryOutcome.Failure(raw.message)
        is Raw.Ok -> HistoryOutcome.Success(parseRevisions(raw.json))
    }

    // ===== 使用者 =====

    suspend fun registerUser(
        name: String,
        secret: String,
        registrationKey: String
    ): UserOutcome = userAction(
        JSONObject()
            .put("action", "registerUser")
            .put("name", name)
            .put("secret", secret)
            .put("registrationKey", registrationKey)
    )

    suspend fun verifyUser(name: String, secret: String): UserOutcome = userAction(
        JSONObject().put("action", "verifyUser").put("name", name).put("secret", secret)
    )

    suspend fun updateUser(
        name: String,
        secret: String,
        newName: String?,
        newSecret: String?
    ): UserOutcome = userAction(
        JSONObject()
            .put("action", "updateUser")
            .put("name", name)
            .put("secret", secret)
            .put("newName", newName.orEmpty())
            .put("newSecret", newSecret.orEmpty())
    )

    private suspend fun userAction(payload: JSONObject): UserOutcome =
        when (val raw = post(payload, "user")) {
            is Raw.Err -> UserOutcome.Failure(
                message = raw.message,
                staleCredential = raw.code == "UNKNOWN_USER" || raw.code == "BAD_PASSWORD"
            )
            is Raw.Ok -> UserOutcome.Success(raw.json.optString("name"))
        }

    // ===== 通信 =====

    private sealed interface Raw {
        data class Ok(val json: JSONObject) : Raw
        data class Err(
            val message: String,
            val code: String = "",
            val kind: String = "",
            val details: List<String> = emptyList()
        ) : Raw
    }

    private suspend fun get(action: String, expectedKind: String): Raw {
        val url = driveUrlOrNull() ?: return missingUrl()
        val separator = if (url.contains('?')) "&" else "?"
        return execute(
            Request.Builder().url("$url${separator}action=$action").get().build(),
            expectedKind
        )
    }

    /**
     * POST の前に対応状況を確かめる。旧スクリプトは未知の action を
     * 測定記録として書き込んでしまい、記録が汚れるため。
     * 一度確認できたらアプリを起動している間は覚えておく。
     */
    private suspend fun post(payload: JSONObject, expectedKind: String): Raw {
        val url = driveUrlOrNull() ?: return missingUrl()
        if (!supportChecked) {
            val support = get("pullEquipment", "equipment")
            if (support is Raw.Err) return support
            supportChecked = true
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        return execute(Request.Builder().url(url).post(body).build(), expectedKind)
    }

    private suspend fun driveUrlOrNull(): String? = prefs.driveUrl.first().takeIf { it.isNotBlank() }

    private fun missingUrl() = Raw.Err(
        "アップロード先 URL が未設定です。設定画面で URL を入力してください"
    )

    private suspend fun execute(request: Request, expectedKind: String): Raw =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Raw.Err(httpErrorMessage(response.code))
                    }
                    val json = runCatching { JSONObject(text) }.getOrNull()
                        ?: return@withContext Raw.Err(OUTDATED_SCRIPT_MESSAGE)
                    // kind の有無だけでは、認証を持たない旧版を見分けられない。
                    // 世代番号で「認証と世代管理に対応した版か」を確かめる。
                    if (json.optInt("apiVersion") < REQUIRED_API_VERSION) {
                        return@withContext Raw.Err(OUTDATED_SCRIPT_MESSAGE)
                    }
                    when {
                        json.optString("status") == "ok" && json.optString("kind") == expectedKind ->
                            Raw.Ok(json)
                        json.optString("status") == "error" -> Raw.Err(
                            message = json.optString(
                                "message",
                                "ドライブ側でエラーが発生しました。管理者に連絡してください"
                            ),
                            code = json.optString("code"),
                            kind = json.optString("kind"),
                            details = json.optJSONArray("removedNames").toStringList()
                        )
                        else -> Raw.Err(
                            "ドライブから予期しない応答が返りました" +
                                "（${expectedKind} を期待、${json.optString("kind").ifBlank { "不明" }} が返りました）"
                        )
                    }
                }
            } catch (e: UnknownHostException) {
                Raw.Err(OFFLINE_SYNC_MESSAGE)
            } catch (e: SocketTimeoutException) {
                Raw.Err("ドライブへの接続がタイムアウトしました。電波の良い場所でもう一度お試しください")
            } catch (e: IOException) {
                Raw.Err(OFFLINE_SYNC_MESSAGE)
            } catch (e: Exception) {
                Raw.Err("ドライブと通信できませんでした（${e.message ?: e.javaClass.simpleName}）")
            }
        }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) add(optString(i)) }
    }

    private fun parseMaster(json: JSONObject): EquipmentMaster {
        val array = json.optJSONArray("equipment") ?: JSONArray()
        var skipped = 0
        val list = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                val parsed = item?.let { toEntityOrNull(it) }
                if (parsed == null) skipped++ else add(parsed)
            }
        }
        return EquipmentMaster(
            revision = json.optInt("revision"),
            updatedAt = json.optLong("updatedAt"),
            updatedBy = json.optString("updatedBy"),
            equipment = list,
            skippedCount = skipped
        )
    }

    /**
     * 規格値が欠けた設備は取り込まない。
     * 下限も上限も 0 のまま取り込むと、どんな測定値でも合格になってしまう。
     */
    private fun toEntityOrNull(item: JSONObject): EquipmentEntity? {
        val uuid = item.optString("uuid")
        val name = item.optString("name")
        if (uuid.isBlank() || name.isBlank()) return null
        val entity = fromJson(uuid, name, item)
        return if (entity.hasUsableSpec) entity else null
    }

    private fun parseRevisions(json: JSONObject): List<EquipmentRevision> {
        val array = json.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    EquipmentRevision(
                        revision = item.optInt("revision"),
                        updatedAt = item.optLong("updatedAt"),
                        updatedBy = item.optString("updatedBy").ifBlank { "不明" },
                        summary = item.optString("summary").ifBlank { "変更内容の記録がありません" },
                        equipmentCount = item.optInt("equipmentCount")
                    )
                )
            }
        }
    }

    private fun fromJson(uuid: String, name: String, item: JSONObject) = EquipmentEntity(
        uuid = uuid,
        name = name,
        massPerMeter = item.optDouble("massPerMeter", 0.0),
        spanMeters = item.optDouble("spanMeters", 0.0),
        standardTension = item.optDouble("standardTension", 0.0),
        specLower = item.optDouble("specLower", 0.0),
        specUpper = item.optDouble("specUpper", 0.0),
        useHzMode = item.optBoolean("useHzMode", false),
        standardHz = item.optDouble("standardHz", 0.0),
        specHzLower = item.optDouble("specHzLower", 0.0),
        specHzUpper = item.optDouble("specHzUpper", 0.0),
        widthMm = item.optDouble("widthMm", 0.0),
        thicknessMm = item.optDouble("thicknessMm", 0.0),
        density = item.optDouble("density", 7850.0),
        youngModulusGpa = item.optDouble("youngModulusGpa", 210.0),
        materialId = item.optString("materialId", BladeMaterialPreset.CARBON_STEEL.id),
        vibrationMode = item.optInt("vibrationMode", 1).coerceAtLeast(1),
        edgewiseBending = item.optBoolean("edgewiseBending", true)
    )

    private fun toJson(eq: EquipmentEntity) = JSONObject().apply {
        put("uuid", eq.uuid)
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
        put("widthMm", eq.widthMm)
        put("thicknessMm", eq.thicknessMm)
        put("density", eq.density)
        put("youngModulusGpa", eq.youngModulusGpa)
        put("materialId", eq.materialId)
        put("vibrationMode", eq.vibrationMode)
        put("edgewiseBending", eq.edgewiseBending)
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        in 400..404 -> "ドライブの URL が正しくありません（HTTP $code）。設定画面の URL を管理者に確認してください"
        in 500..599 -> "ドライブ側でエラーが発生しました（HTTP $code）。時間をおいてもう一度お試しください"
        else -> "ドライブと通信できませんでした（HTTP $code）"
    }

    private companion object {
        const val SYNC_TIMEOUT_SECONDS = 20L

        /** drive-upload.gs の API_VERSION がこれ以上でなければ使わせない。 */
        const val REQUIRED_API_VERSION = 2
        const val OFFLINE_SYNC_MESSAGE =
            "ネットワークにつながっていません。Wi-Fi や電波の届く場所でもう一度お試しください"
        const val OUTDATED_SCRIPT_MESSAGE =
            "ドライブのスクリプトが最新ではありません。管理者に drive-upload.gs の再デプロイを依頼してください"
    }
}
