package com.bandknife.tension.upload

import android.content.Context
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.data.PreferencesManager
import com.bandknife.tension.data.UploadQueueDao
import com.bandknife.tension.data.UploadQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val OFFLINE_MESSAGE =
    "ネットワークにつながっていません。データは端末に保存されています。Wi-Fi に接続してから「未送信データを送る」を押してください"

data class UploadResult(
    val success: Boolean,
    val message: String
)

data class QueueUploadResult(
    val sentCount: Int,
    val remainingCount: Int,
    val message: String
)

class DriveUploader(
    context: Context,
    private val queueDao: UploadQueueDao
) {
    private val prefs = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 送信キューに積むだけで通信は待たない。
     * 圏外の機械室で「記録」を押したときに、通信タイムアウト分だけ画面が固まるのを避ける。
     */
    suspend fun enqueueIfEnabled(record: MeasurementRecordEntity): Boolean {
        if (prefs.localMode.first() || record.localOnly) return false
        val enabled = prefs.driveEnabled.first()
        if (!enabled) return false
        val payload = recordToJson(record)
        queueDao.insert(UploadQueueEntity(payloadJson = payload.toString()))
        return true
    }

    suspend fun processQueue(): QueueUploadResult {
        if (prefs.localMode.first()) {
            val remaining = queueDao.count()
            return QueueUploadResult(0, remaining, "ローカルモード中はアップロードしません")
        }
        val url = prefs.driveUrl.first()
        if (url.isBlank()) {
            val remaining = queueDao.count()
            return QueueUploadResult(0, remaining, "アップロード先 URL が未設定です。設定画面で URL を入力してください")
        }
        val items = queueDao.getAll()
        if (items.isEmpty()) {
            return QueueUploadResult(0, 0, "未送信のデータはありません")
        }
        var sent = 0
        var lastError = "送信に失敗しました"
        for (item in items) {
            val result = postForQueue(url, item.payloadJson)
            if (result.success) {
                queueDao.delete(item.id)
                sent++
            } else {
                lastError = result.message
            }
        }
        val remaining = queueDao.count()
        val message = when {
            sent > 0 && remaining == 0 -> "${sent}件を送信しました"
            sent > 0 -> "${sent}件を送信しました（未送信: ${remaining}件）"
            else -> lastError
        }
        return QueueUploadResult(sent, remaining, message)
    }

    /** Repository からキュー処理と共有フォルダ反映をまとめて行うための POST。 */
    suspend fun postForQueue(url: String, json: String): UploadResult = post(url, json)

    suspend fun testUpload(): UploadResult {
        if (prefs.localMode.first()) {
            return UploadResult(false, "ローカルモード中はアップロードできません")
        }
        val url = prefs.driveUrl.first()
        if (url.isBlank()) {
            return UploadResult(false, "アップロード先 URL が未設定です。設定画面で URL を入力してください")
        }
        val test = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("equipmentName", "テスト")
            put("frequencyHz", 85.0)
            put("tensionN", 160.0)
            put("passed", true)
            put("sampleCount", 1)
            put("stdDev", 0.0)
            put("ci95Lower", 160.0)
            put("ci95Upper", 160.0)
            put("comment", "テスト送信")
        }
        return post(url, test.toString())
    }

    private suspend fun post(url: String, json: String): UploadResult = withContext(Dispatchers.IO) {
        try {
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext UploadResult(false, httpErrorMessage(response.code))
                }
                val jsonResponse = runCatching { JSONObject(responseBody) }.getOrNull()
                when (jsonResponse?.optString("status")) {
                    "ok" -> UploadResult(true, "送信に成功しました")
                    "error" -> UploadResult(
                        false,
                        jsonResponse.optString("message", "送信先でエラーが発生しました。管理者に連絡してください")
                    )
                    else -> UploadResult(
                        false,
                        "アップロード先から想定外の応答が返りました。URL が正しいか管理者に確認してください"
                    )
                }
            }
        } catch (e: UnknownHostException) {
            UploadResult(false, OFFLINE_MESSAGE)
        } catch (e: SocketTimeoutException) {
            UploadResult(false, "送信がタイムアウトしました。データは端末に残っています。電波の良い場所で「未送信データを送る」を押してください")
        } catch (e: IOException) {
            UploadResult(false, OFFLINE_MESSAGE)
        } catch (e: Exception) {
            UploadResult(false, "送信できませんでした（${e.message ?: e.javaClass.simpleName}）。データは端末に残っています")
        }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        in 400..404 -> "アップロード先の URL が正しくありません（HTTP $code）。管理者に連絡し、設定画面の URL を確認してください"
        in 500..599 -> "アップロード先でエラーが発生しました（HTTP $code）。データは端末に残っています。時間をおいて「未送信データを送る」を押してください"
        else -> "送信できませんでした（HTTP $code）。データは端末に残っています"
    }

    private fun recordToJson(record: MeasurementRecordEntity): JSONObject {
        return JSONObject().apply {
            put("timestamp", record.timestamp)
            put("equipmentName", record.equipmentName)
            put("frequencyHz", record.frequencyHz)
            put("tensionN", record.tensionN)
            put("passed", record.passed)
            put("sampleCount", record.sampleCount)
            put("stdDev", record.stdDev)
            put("ci95Lower", record.ci95Lower)
            put("ci95Upper", record.ci95Upper)
            put("comment", record.comment)
        }
    }
}
