package com.bandknife.tension.upload

import android.content.Context
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.data.PreferencesManager
import com.bandknife.tension.data.UploadQueueDao
import com.bandknife.tension.data.UploadQueueEntity
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DriveUploader(
    context: Context,
    private val queueDao: UploadQueueDao
) {
    private val prefs = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun enqueueIfEnabled(record: MeasurementRecordEntity) {
        val enabled = prefs.driveEnabled.first()
        if (!enabled) return
        val payload = recordToJson(record)
        queueDao.insert(UploadQueueEntity(payloadJson = payload.toString()))
        processQueue()
    }

    suspend fun processQueue() {
        val url = prefs.driveUrl.first()
        if (url.isBlank()) return
        val items = queueDao.getAll()
        for (item in items) {
            val success = post(url, item.payloadJson)
            if (success) queueDao.delete(item.id)
        }
    }

    suspend fun testUpload(): Boolean {
        val url = prefs.driveUrl.first()
        if (url.isBlank()) return false
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

    private fun post(url: String, json: String): Boolean {
        return try {
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
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
