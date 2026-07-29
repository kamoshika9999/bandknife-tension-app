package com.bandknife.tension.audio

import android.content.Context
import android.os.Build
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.bandknife.tension.domain.TapAnalysis
import com.bandknife.tension.domain.TapQualityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class AudioAnalysisResult(
    val frequencyHz: Double = 0.0,
    val amplitude: Double = 0.0,
    val spectrum: FloatArray = FloatArray(0),
    val tapAnalysis: TapAnalysis? = null,
    val noiseLevelDb: Double = 0.0,
    val micName: String = "USBマイク未接続",
    // 物理的な打撃1回ごとに増えるID。響き続く間のバッファは同じIDを持つ
    val tapId: Long = 0,
    val signalQuality: Double = 0.0,
    val stackedTapCount: Int = 0,
    val stackedFrequencyHz: Double = 0.0,
    val tapFrequencyHz: Double = 0.0,
    /** 打撃が終わったときだけ増える。記録用の周波数はこちらを使う */
    val tapFinalizedId: Long = 0,
    val finalizedTapFrequencyHz: Double = 0.0,
    val finalizedTapAnalysis: TapAnalysis? = null
)

class AudioAnalyzer(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 44100
        // 1 m スパンで基本周波数が約 7 Hz になるため、低周波を分解能よく捉える
        const val BUFFER_SIZE = 8192
        const val MIN_FREQ = 5.0
        const val MAX_FREQ = 300.0
    }

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var sensitivityThreshold = 0.08
    private var preferredDeviceId: Int? = null
    private var allowBuiltinMic = false
    private var currentTapId = 0L
    private var wasAboveThreshold = false
    private var bestTapAnalysis: TapAnalysis? = null
    private var bestTapAmplitude = 0.0
    private val signalProcessor = SignalProcessor()

    fun beginNoiseCalibration() = signalProcessor.beginNoiseCalibration()

    fun finalizeNoiseCalibration(): Double = signalProcessor.finalizeNoiseCalibration()

    fun resetTapAccumulation() {
        currentTapId = 0L
        wasAboveThreshold = false
        bestTapAnalysis = null
        bestTapAmplitude = 0.0
        signalProcessor.resetTapAccumulation()
    }

    fun markTapForStacking(tapId: Long) = signalProcessor.markTapForStacking(tapId)

    private val _result = MutableStateFlow(AudioAnalysisResult())
    val result: StateFlow<AudioAnalysisResult> = _result

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun setSensitivity(value: Double) {
        sensitivityThreshold = value.coerceIn(0.01, 0.5)
    }

    fun setPreferredDevice(deviceId: Int?) {
        preferredDeviceId = deviceId
    }

    fun setAllowBuiltinMic(allowed: Boolean) {
        allowBuiltinMic = allowed
    }

    fun listInputDevices(): List<Pair<Int, String>> {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter(::isUsbInputDevice)
            .map { it.id to deviceLabel(it) }
            .toMutableList()
        if (allowBuiltinMic) {
            findBuiltinMic(manager)?.let { builtin ->
                devices.add(builtin.id to "${deviceLabel(builtin)}（特別モード）")
            }
        }
        return devices
    }

    fun hasUsableMicrophone(): Boolean = resolveInputDeviceId(null) != null

    fun resolveInputDeviceId(savedDeviceId: Int?): Int? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val usbDevices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter(::isUsbInputDevice)
        val builtinMic = if (allowBuiltinMic) findBuiltinMic(manager) else null

        savedDeviceId?.let { id ->
            usbDevices.find { it.id == id }?.let { return id }
            if (builtinMic?.id == id) return id
        }

        usbDevices.firstOrNull()?.let { return it.id }
        return builtinMic?.id
    }

    private fun findBuiltinMic(manager: AudioManager): AudioDeviceInfo? {
        val builtins = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return builtins.firstOrNull { it.address.contains("bottom", ignoreCase = true) }
            ?: builtins.firstOrNull()
    }

    private fun isUsbInputDevice(device: AudioDeviceInfo): Boolean = when (device.type) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    private fun deviceLabel(device: AudioDeviceInfo): String = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内蔵マイク"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有線ヘッドセット"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USBマイク"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetoothマイク"
        else -> if (Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            "Bluetoothマイク"
        } else {
            "マイク (${device.type})"
        }
    }

    fun measureNoiseLevel(onComplete: (Double) -> Unit) {
        scope.launch {
            val level = captureNoiseLevel()
            onComplete(level)
        }
    }

    private suspend fun captureNoiseLevel(): Double {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = createAudioRecord(minBuf) ?: return 0.0
        val buffer = ShortArray(BUFFER_SIZE)
        try {
            record.startRecording()
            var sum = 0.0
            repeat(10) {
                val read = record.read(buffer, 0, BUFFER_SIZE)
                if (read > 0) {
                    sum += buffer.take(read).map { abs(it.toDouble()) }.average()
                }
            }
            val normalized = sum / 10.0 / Short.MAX_VALUE
            return 20 * kotlin.math.log10(normalized.coerceAtLeast(1e-6))
        } finally {
            record.stop()
            record.release()
        }
    }

    fun start(): Boolean {
        if (_isRecording.value) return true
        val deviceId = preferredDeviceId ?: resolveInputDeviceId(null)
        if (deviceId == null) return false
        preferredDeviceId = deviceId
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = createAudioRecord(minBuf) ?: return false
        audioRecord = record
        record.startRecording()
        _isRecording.value = true

        job = scope.launch {
            val buffer = ShortArray(BUFFER_SIZE)
            val window = FftEngine.hammingWindow(BUFFER_SIZE)
            while (isActive && _isRecording.value) {
                val read = record.read(buffer, 0, BUFFER_SIZE)
                if (read > 0) {
                    processBuffer(buffer, window)
                }
            }
        }
        return true
    }

    fun stop() {
        _isRecording.value = false
        job?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun createAudioRecord(minBuf: Int): AudioRecord? {
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, BUFFER_SIZE * 2)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        preferredDeviceId?.let { id ->
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .find { it.id == id }
                ?.let { record.preferredDevice = it }
        }
        return record
    }

    private fun processBuffer(samples: ShortArray, window: DoubleArray) {
        val mags = computeMagnitudes(samples, window)
        val minBin = (MIN_FREQ * BUFFER_SIZE / SAMPLE_RATE).toInt().coerceAtLeast(1)
        val maxBin = (MAX_FREQ * BUFFER_SIZE / SAMPLE_RATE).toInt().coerceAtMost(mags.size - 2)
        val maxAmp = samples.maxOf { abs(it.toInt()) }.toDouble()
        val normalized = maxAmp / Short.MAX_VALUE

        if (normalized < sensitivityThreshold) {
            if (wasAboveThreshold) {
                val endedTapId = currentTapId
                signalProcessor.endTap()
                val finalizedFrequency = signalProcessor.frequencyFromFinalizedTap(
                    endedTapId, SAMPLE_RATE, BUFFER_SIZE, minBin, maxBin
                )
                val finalizedAnalysis = bestTapAnalysis?.copy(
                    frequencyHz = finalizedFrequency ?: bestTapAnalysis!!.frequencyHz
                )
                wasAboveThreshold = false
                bestTapAnalysis = null
                bestTapAmplitude = 0.0
                if (finalizedFrequency != null && finalizedAnalysis != null) {
                    val cleaned = signalProcessor.subtractNoise(mags, minBin, maxBin)
                    emitResult(
                        cleaned, minBin, maxBin, normalized,
                        finalizedFrequency, finalizedFrequency, finalizedAnalysis,
                        tapFinalizedId = endedTapId,
                        finalizedTapFrequencyHz = finalizedFrequency,
                        finalizedTapAnalysis = finalizedAnalysis
                    )
                }
            }
            signalProcessor.accumulateNoise(mags, minBin, maxBin)
            return
        }

        if (!wasAboveThreshold) {
            currentTapId++
            wasAboveThreshold = true
            bestTapAnalysis = null
            bestTapAmplitude = 0.0
            signalProcessor.beginTap(currentTapId)
        }

        val cleaned = signalProcessor.subtractNoise(mags, minBin, maxBin)
        signalProcessor.accumulateTapSpectrum(cleaned, minBin, maxBin)

        val (peakIdx, peakMag) = signalProcessor.findPeak(cleaned, minBin, maxBin)
        val singleFrequency = signalProcessor.frequencyFromPeak(peakIdx, cleaned, SAMPLE_RATE, BUFFER_SIZE)
        val accumulatedFrequency = signalProcessor.frequencyFromAccumulatedTap(
            SAMPLE_RATE, BUFFER_SIZE, minBin, maxBin
        ) ?: singleFrequency
        val stackedFrequency = signalProcessor.stackedFrequency(SAMPLE_RATE, BUFFER_SIZE, minBin, maxBin)

        val harmonicBin = (peakIdx * 2).coerceAtMost(cleaned.size - 1)
        val harmonicMag = cleaned[harmonicBin]

        val hadDouble = detectDoubleHit(samples)
        val tap = TapQualityChecker.analyze(
            samples, accumulatedFrequency, peakMag, harmonicMag, maxAmp, hadDouble
        )
        if (normalized >= bestTapAmplitude) {
            bestTapAmplitude = normalized
            bestTapAnalysis = tap
        }

        emitResult(
            cleaned, minBin, maxBin, normalized,
            stackedFrequency ?: accumulatedFrequency, singleFrequency, tap
        )
    }

    private fun emitResult(
        cleaned: DoubleArray,
        minBin: Int,
        maxBin: Int,
        normalized: Double,
        displayFrequency: Double,
        singleFrequency: Double,
        tap: TapAnalysis,
        tapFinalizedId: Long = 0,
        finalizedTapFrequencyHz: Double = 0.0,
        finalizedTapAnalysis: TapAnalysis? = null
    ) {
        val peakMag = signalProcessor.findPeak(cleaned, minBin, maxBin).second
        val spectrum = signalProcessor.toDisplaySpectrum(cleaned, minBin, maxBin)
        val signalQuality = signalProcessor.estimateSignalQuality(peakMag)

        val micName = audioRecord?.preferredDevice?.let { deviceLabel(it) }
            ?: audioRecord?.routedDevice?.let { deviceLabel(it) }
            ?: "USBマイク未接続"

        _result.value = AudioAnalysisResult(
            frequencyHz = displayFrequency,
            amplitude = normalized,
            spectrum = spectrum,
            tapAnalysis = tap,
            micName = micName,
            tapId = currentTapId,
            signalQuality = signalQuality,
            stackedTapCount = signalProcessor.stackedTapCount,
            stackedFrequencyHz = signalProcessor.stackedFrequency(SAMPLE_RATE, BUFFER_SIZE, minBin, maxBin) ?: 0.0,
            tapFrequencyHz = singleFrequency,
            tapFinalizedId = tapFinalizedId,
            finalizedTapFrequencyHz = finalizedTapFrequencyHz,
            finalizedTapAnalysis = finalizedTapAnalysis
        )
    }

    private fun computeMagnitudes(samples: ShortArray, window: DoubleArray): DoubleArray {
        val real = DoubleArray(BUFFER_SIZE)
        val imag = DoubleArray(BUFFER_SIZE) { 0.0 }
        for (i in samples.indices) {
            real[i] = samples[i].toDouble() * window[i]
        }
        FftEngine.fft(real, imag)
        return FftEngine.magnitudes(real, imag)
    }

    private fun detectDoubleHit(samples: ShortArray): Boolean {
        // 振幅エンベロープが「減衰したあと再び立ち上がる」場合のみ跳ね返りと判定する。
        // 刃の自由振動の山を打撃と数えると、響いているだけで常に DOUBLE_HIT になってしまう
        val chunk = 256
        val n = samples.size / chunk
        if (n < 4) return false
        val raw = DoubleArray(n) { i ->
            var m = 0
            for (j in i * chunk until (i + 1) * chunk) {
                val v = abs(samples[j].toInt())
                if (v > m) m = v
            }
            m.toDouble()
        }
        // 低周波振動の谷をエンベロープの減衰と誤認しないよう隣接チャンクで平滑化
        val env = DoubleArray(n - 1) { i -> max(raw[i], raw[i + 1]) }
        val peak = env.max()
        if (peak <= 0.0) return false
        var peakSeen = false
        var decayed = false
        for (value in env) {
            when {
                !peakSeen -> if (value >= peak * 0.99) peakSeen = true
                !decayed -> if (value < peak * 0.4) decayed = true
                else -> if (value > peak * 0.7) return true
            }
        }
        return false
    }
}
