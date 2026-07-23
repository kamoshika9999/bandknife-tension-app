package com.bandknife.tension.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppMode { SIMPLE, ADVANCE, DETAIL }

const val DEFAULT_DRIVE_URL =
    "https://script.google.com/macros/s/AKfycbzEU9b-I9zWOiAE27l_VFxJucOWk8ViCZdeA2KgVLPZKd5rBNyDvUwF9y5vzg9NRPI_/exec"

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
        val VIBRATION = booleanPreferencesKey("vibration")
        val FIRST_RUN = booleanPreferencesKey("first_run")
    }

    val appMode: Flow<AppMode> = context.dataStore.data.map {
        AppMode.valueOf(it[Keys.APP_MODE] ?: AppMode.SIMPLE.name)
    }

    val useKgf: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_KGF] ?: false }
    val sensitivity: Flow<Double> = context.dataStore.data.map { it[Keys.SENSITIVITY] ?: 0.08 }
    val continuousCount: Flow<Int> = context.dataStore.data.map { it[Keys.CONTINUOUS_COUNT] ?: 5 }
    val driveUrl: Flow<String> = context.dataStore.data.map { it[Keys.DRIVE_URL] ?: DEFAULT_DRIVE_URL }
    val driveEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DRIVE_ENABLED] ?: false }
    val micDeviceId: Flow<Int> = context.dataStore.data.map { it[Keys.MIC_DEVICE_ID] ?: -1 }
    val micAuto: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIC_AUTO] ?: true }
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIBRATION] ?: true }
    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_RUN] ?: true }

    suspend fun setAppMode(mode: AppMode) = edit(Keys.APP_MODE, mode.name)
    suspend fun setUseKgf(value: Boolean) = edit(Keys.USE_KGF, value)
    suspend fun setSensitivity(value: Double) = edit(Keys.SENSITIVITY, value)
    suspend fun setContinuousCount(value: Int) = edit(Keys.CONTINUOUS_COUNT, value)
    suspend fun setDriveUrl(value: String) = edit(Keys.DRIVE_URL, value)
    suspend fun setDriveEnabled(value: Boolean) = edit(Keys.DRIVE_ENABLED, value)
    suspend fun setMicDeviceId(value: Int) = edit(Keys.MIC_DEVICE_ID, value)
    suspend fun setMicAuto(value: Boolean) = edit(Keys.MIC_AUTO, value)
    suspend fun setVibration(value: Boolean) = edit(Keys.VIBRATION, value)
    suspend fun setFirstRunDone() = edit(Keys.FIRST_RUN, false)

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
