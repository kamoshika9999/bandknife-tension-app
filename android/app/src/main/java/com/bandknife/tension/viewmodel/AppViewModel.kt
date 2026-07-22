package com.bandknife.tension.viewmodel

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bandknife.tension.audio.AudioAnalyzer
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.data.PreferencesManager
import com.bandknife.tension.data.Repository
import com.bandknife.tension.domain.MeasurementStats
import com.bandknife.tension.domain.StatisticsEngine
import com.bandknife.tension.domain.TapQuality
import com.bandknife.tension.domain.TensionCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class MeasureMode { SINGLE, CONTINUOUS }
enum class SimpleStep { SELECT, MEASURE, RESULT }

data class ContinuousState(
    val targetCount: Int = 5,
    val currentCount: Int = 0,
    val values: List<Double> = emptyList(),
    val frequencies: List<Double> = emptyList(),
    val stats: MeasurementStats? = null,
    val lastTapMessage: String = ""
)

data class MeasureUiState(
    val frequencyHz: Double = 0.0,
    val tensionN: Double = 0.0,
    val passed: Boolean = false,
    val micName: String = "",
    val spectrum: FloatArray = FloatArray(0),
    val tapMessage: String = "",
    val tapQuality: TapQuality? = null,
    val amplitude: Double = 0.0,
    val isMeasuring: Boolean = false,
    val noiseWarning: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as com.bandknife.tension.TensionApp).repository
    private val audio = AudioAnalyzer(app)
    private val vibrator = app.getSystemService(VibratorManager::class.java)?.defaultVibrator

    val equipment = repository.equipment.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records = repository.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val appMode = repository.preferences.appMode.stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.SIMPLE)
    val useKgf = repository.preferences.useKgf.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val continuousCount = repository.preferences.continuousCount.stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    private val _selectedEquipment = MutableStateFlow<EquipmentEntity?>(null)
    val selectedEquipment: StateFlow<EquipmentEntity?> = _selectedEquipment.asStateFlow()

    private val _measureState = MutableStateFlow(MeasureUiState())
    val measureState: StateFlow<MeasureUiState> = _measureState.asStateFlow()

    private val _measureMode = MutableStateFlow(MeasureMode.CONTINUOUS)
    val measureMode: StateFlow<MeasureMode> = _measureMode.asStateFlow()

    private val _continuous = MutableStateFlow(ContinuousState())
    val continuous: StateFlow<ContinuousState> = _continuous.asStateFlow()

    private val _simpleStep = MutableStateFlow(SimpleStep.SELECT)
    val simpleStep: StateFlow<SimpleStep> = _simpleStep.asStateFlow()

    private var audioJob: Job? = null

    init {
        viewModelScope.launch {
            combine(audio.result, audio.isRecording) { result, recording ->
                result to recording
            }.collect { (result, recording) ->
                val eq = _selectedEquipment.value ?: return@collect
                val tension = repository.tensionFor(eq, result.frequencyHz)
                val passed = if (result.frequencyHz > 0) repository.evaluate(eq, result.frequencyHz, tension) else false
                _measureState.value = MeasureUiState(
                    frequencyHz = result.frequencyHz,
                    tensionN = tension,
                    passed = passed,
                    micName = result.micName,
                    spectrum = result.spectrum,
                    tapMessage = result.tapAnalysis?.message ?: "",
                    tapQuality = result.tapAnalysis?.quality,
                    amplitude = result.amplitude,
                    isMeasuring = recording
                )
                if (recording && result.tapAnalysis != null && result.frequencyHz > 0) {
                    handleTap(result.frequencyHz, tension, result.tapAnalysis.quality, result.tapAnalysis.message)
                }
            }
        }
    }

    fun selectEquipment(eq: EquipmentEntity) {
        _selectedEquipment.value = eq
    }

    fun setMeasureMode(mode: MeasureMode) {
        _measureMode.value = mode
        resetContinuous()
    }

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch { repository.preferences.setAppMode(mode) }
    }

    fun setSimpleStep(step: SimpleStep) {
        _simpleStep.value = step
    }

    fun startMeasuring() {
        viewModelScope.launch {
            val sensitivity = repository.preferences.sensitivity.first()
            audio.setSensitivity(sensitivity)
            audio.measureNoiseLevel { db ->
                val warning = if (db > -20) "周囲が騒がしいため測定精度が落ちる可能性があります" else null
                _measureState.value = _measureState.value.copy(noiseWarning = warning)
            }
            audio.start()
            resetContinuous()
        }
    }

    fun stopMeasuring() {
        audio.stop()
    }

    private fun handleTap(freq: Double, tension: Double, quality: TapQuality, message: String) {
        if (quality != TapQuality.GOOD) {
            _continuous.value = _continuous.value.copy(lastTapMessage = message)
            return
        }
        vibrateShort()
        when (_measureMode.value) {
            MeasureMode.SINGLE -> {
                _continuous.value = ContinuousState(
                    targetCount = 1,
                    currentCount = 1,
                    values = listOf(tension),
                    frequencies = listOf(freq),
                    stats = StatisticsEngine.analyze(listOf(tension), false)
                )
            }
            MeasureMode.CONTINUOUS -> {
                val current = _continuous.value
                if (current.currentCount >= current.targetCount) return
                val newValues = current.values + tension
                val newFreqs = current.frequencies + freq
                val count = current.currentCount + 1
                val stats = if (count >= current.targetCount) StatisticsEngine.analyze(newValues) else null
                _continuous.value = current.copy(
                    currentCount = count,
                    values = newValues,
                    frequencies = newFreqs,
                    stats = stats,
                    lastTapMessage = "適正な打撃 ($count/${current.targetCount})"
                )
                if (stats != null) vibrateLong()
            }
        }
    }

    fun resetContinuous() {
        viewModelScope.launch {
            val count = repository.preferences.continuousCount.first()
            _continuous.value = ContinuousState(targetCount = count)
        }
    }

    fun setContinuousTarget(count: Int) {
        viewModelScope.launch {
            repository.preferences.setContinuousCount(count)
            _continuous.value = _continuous.value.copy(targetCount = count)
        }
    }

    suspend fun saveCurrentRecord(comment: String = ""): Long? {
        val eq = _selectedEquipment.value ?: return null
        val cont = _continuous.value
        val stats = cont.stats ?: return null
        val record = MeasurementRecordEntity(
            equipmentId = eq.id,
            equipmentName = eq.name,
            frequencyHz = cont.frequencies.average(),
            tensionN = stats.mean,
            passed = repository.evaluate(eq, cont.frequencies.average(), stats.mean),
            sampleCount = stats.values.size,
            stdDev = stats.stdDev,
            ci95Lower = stats.ci95Lower,
            ci95Upper = stats.ci95Upper,
            comment = comment,
            rawValuesJson = JSONArray(cont.values).toString()
        )
        return repository.saveRecord(record)
    }

    fun formatTension(n: Double): String {
        val useKgf = useKgf.value
        return if (useKgf) String.format("%.1f kgf", TensionCalculator.newtonsToKgf(n))
        else String.format("%.0f N", n)
    }

    fun updateRecordComment(record: MeasurementRecordEntity, comment: String) {
        if (record.isLocked) return
        viewModelScope.launch {
            repository.updateRecord(record.copy(comment = comment))
        }
    }

    fun toggleRecordLock(record: MeasurementRecordEntity) {
        viewModelScope.launch {
            repository.updateRecord(record.copy(isLocked = !record.isLocked))
        }
    }

    fun deleteRecord(record: MeasurementRecordEntity) {
        viewModelScope.launch { repository.deleteRecord(record) }
    }

    fun deleteRecordsInRange(from: Long, to: Long) {
        viewModelScope.launch { repository.deleteRecordsInRange(from, to) }
    }

    private fun vibrateShort() {
        viewModelScope.launch {
            if (!repository.preferences.vibrationEnabled.first()) return@launch
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrateLong() {
        viewModelScope.launch {
            if (!repository.preferences.vibrationEnabled.first()) return@launch
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun saveRecord() {
        viewModelScope.launch { saveCurrentRecord() }
    }

    fun saveAndResetToSelect() {
        viewModelScope.launch {
            saveCurrentRecord()
            resetContinuous()
            setSimpleStep(SimpleStep.SELECT)
        }
    }

    fun getInputDevices() = audio.listInputDevices()

    override fun onCleared() {
        audio.stop()
        super.onCleared()
    }
}
