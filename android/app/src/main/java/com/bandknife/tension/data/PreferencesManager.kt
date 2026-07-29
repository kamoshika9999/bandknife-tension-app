package com.bandknife.tension.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppMode { SIMPLE, ADVANCE, DETAIL }

/** 組織ごとに設定画面で入力する GAS デプロイ URL。未設定時は送信しない。 */
const val DEFAULT_DRIVE_URL = ""

private fun normalizeDriveUrl(url: String?): String {
    val value = url?.trim().orEmpty()
    if (value.isBlank()) return ""
    // コピー時の l / I 取り違えで保存された既知の誤URLを空に戻し、再入力を促す
    if (value.contains("AKfycbzEU9b-l9z", ignoreCase = true)) return ""
    return value
}

private val Context.dataStore by preferencesDataStore("settings")

class PreferencesManager(private val context: Context) {
    private object Keys {
        val APP_MODE = stringPreferencesKey("app_mode")
        val USE_KGF = booleanPreferencesKey("use_kgf")
        val SENSITIVITY = doublePreferencesKey("sensitivity")
        val CONTINUOUS_COUNT = intPreferencesKey("continuous_count")
        val DRIVE_URL = stringPreferencesKey("drive_url")
        val DRIVE_ENABLED = booleanPreferencesKey("drive_enabled")
        val MIC_DEVICE_ID = intPreferencesKey("mic_device_id")
        val MIC_AUTO = booleanPreferencesKey("mic_auto")
        /** 通常は USB のみ。有効時のみ内蔵マイクを一時的に許可する。 */
        val BUILTIN_MIC_SPECIAL_MODE = booleanPreferencesKey("builtin_mic_special_mode")
        val VIBRATION = booleanPreferencesKey("vibration")
        val FIRST_RUN = booleanPreferencesKey("first_run")
        val EQUIPMENT_SYNCED_AT = longPreferencesKey("equipment_synced_at")
        val EQUIPMENT_REVISION = intPreferencesKey("equipment_revision")
        /** ドライブ連携なしで端末だけで測定するモード。 */
        val LOCAL_MODE = booleanPreferencesKey("local_mode")
    }

    val appMode: Flow<AppMode> = context.dataStore.data.map {
        AppMode.valueOf(it[Keys.APP_MODE] ?: AppMode.SIMPLE.name)
    }

    val useKgf: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_KGF] ?: false }
    val sensitivity: Flow<Double> = context.dataStore.data.map { it[Keys.SENSITIVITY] ?: 0.08 }
    val continuousCount: Flow<Int> = context.dataStore.data.map { it[Keys.CONTINUOUS_COUNT] ?: 5 }
    val driveUrl: Flow<String> = context.dataStore.data.map { normalizeDriveUrl(it[Keys.DRIVE_URL]) }
    val driveEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DRIVE_ENABLED] ?: false }
    val micDeviceId: Flow<Int> = context.dataStore.data.map { it[Keys.MIC_DEVICE_ID] ?: -1 }
    val micAuto: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIC_AUTO] ?: true }
    val builtinMicSpecialMode: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.BUILTIN_MIC_SPECIAL_MODE] ?: false
    }
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIBRATION] ?: true }
    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_RUN] ?: true }

    /** ドライブの設備マスターと疎通できた最終時刻。0 は一度も確認できていないことを表す。 */
    val equipmentSyncedAt: Flow<Long> = context.dataStore.data.map { it[Keys.EQUIPMENT_SYNCED_AT] ?: 0L }
    val equipmentRevision: Flow<Int> = context.dataStore.data.map { it[Keys.EQUIPMENT_REVISION] ?: 0 }
    val localMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCAL_MODE] ?: false }

    suspend fun setAppMode(mode: AppMode) = edit(Keys.APP_MODE, mode.name)
    suspend fun setUseKgf(value: Boolean) = edit(Keys.USE_KGF, value)
    suspend fun setSensitivity(value: Double) = edit(Keys.SENSITIVITY, value)
    suspend fun setContinuousCount(value: Int) = edit(Keys.CONTINUOUS_COUNT, value)
    suspend fun setDriveUrl(value: String) = edit(Keys.DRIVE_URL, normalizeDriveUrl(value))

    suspend fun ensureDriveUrlNormalized() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DRIVE_URL]
            val normalized = normalizeDriveUrl(current)
            if (current != normalized) {
                prefs[Keys.DRIVE_URL] = normalized
            }
        }
    }
    suspend fun setDriveEnabled(value: Boolean) = edit(Keys.DRIVE_ENABLED, value)
    suspend fun setMicDeviceId(value: Int) = edit(Keys.MIC_DEVICE_ID, value)
    suspend fun setMicAuto(value: Boolean) = edit(Keys.MIC_AUTO, value)
    suspend fun setBuiltinMicSpecialMode(value: Boolean) = edit(Keys.BUILTIN_MIC_SPECIAL_MODE, value)
    suspend fun setVibration(value: Boolean) = edit(Keys.VIBRATION, value)
    suspend fun setFirstRunDone() = edit(Keys.FIRST_RUN, false)
    suspend fun setEquipmentSyncedAt(value: Long) = edit(Keys.EQUIPMENT_SYNCED_AT, value)
    suspend fun setEquipmentRevision(value: Int) = edit(Keys.EQUIPMENT_REVISION, value)
    suspend fun setLocalMode(value: Boolean) = edit(Keys.LOCAL_MODE, value)

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
