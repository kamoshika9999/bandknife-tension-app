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
    val micName: String = "内蔵マイク"
)

class AudioAnalyzer(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE = 2048
        const val MIN_FREQ = 20.0
        const val MAX_FREQ = 300.0
    }

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var sensitivityThreshold = 0.08
    private var preferredDeviceId: Int? = null

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

    fun listInputDevices(): List<Pair<Int, String>> {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val selectable = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter(::isSelectableInputDevice)
        val (builtins, externals) = selectable.partition { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val builtin = builtins.firstOrNull { it.address.contains("bottom", ignoreCase = true) }
            ?: builtins.firstOrNull()
        val result = mutableListOf<Pair<Int, String>>()
        builtin?.let { result.add(it.id to deviceLabel(it)) }
        externals.forEach { result.add(it.id to deviceLabel(it)) }
        return result
    }

    private fun isSelectableInputDevice(device: AudioDeviceInfo): Boolean = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
        else -> Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
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

    fun start() {
        if (_isRecording.value) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = createAudioRecord(minBuf) ?: return
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
        val maxAmp = samples.maxOf { abs(it.toInt()) }.toDouble()
        val normalized = maxAmp / Short.MAX_VALUE
        if (normalized < sensitivityThreshold) return

        val real = DoubleArray(BUFFER_SIZE)
        val imag = DoubleArray(BUFFER_SIZE) { 0.0 }
        for (i in samples.indices) {
            real[i] = samples[i].toDouble() * window[i]
        }
        FftEngine.fft(real, imag)
        val mags = FftEngine.magnitudes(real, imag)

        val minBin = (MIN_FREQ * BUFFER_SIZE / SAMPLE_RATE).toInt().coerceAtLeast(1)
        val maxBin = (MAX_FREQ * BUFFER_SIZE / SAMPLE_RATE).toInt().coerceAtMost(mags.size - 2)

        var peakIdx = minBin
        var peakMag = 0.0
        for (i in minBin..maxBin) {
            if (mags[i] > peakMag) {
                peakMag = mags[i]
                peakIdx = i
            }
        }

        val refinedBin = FftEngine.parabolicInterpolation(mags, peakIdx)
        val frequency = refinedBin * SAMPLE_RATE / BUFFER_SIZE

        val harmonicBin = (peakIdx * 2).coerceAtMost(mags.size - 1)
        val harmonicMag = mags[harmonicBin]

        val hadDouble = detectDoubleHit(samples)
        val tap = TapQualityChecker.analyze(
            samples, frequency, peakMag, harmonicMag, maxAmp, hadDouble
        )

        val spectrum = FloatArray(64) { i ->
            val bin = minBin + (maxBin - minBin) * i / 63
            mags[bin.coerceIn(0, mags.size - 1)].toFloat()
        }

        val micName = audioRecord?.preferredDevice?.let { deviceLabel(it) }
            ?: audioRecord?.routedDevice?.let { deviceLabel(it) }
            ?: "内蔵マイク"

        _result.value = AudioAnalysisResult(
            frequencyHz = frequency,
            amplitude = normalized,
            spectrum = spectrum,
            tapAnalysis = tap,
            micName = micName
        )
    }

    private fun detectDoubleHit(samples: ShortArray): Boolean {
        var peaks = 0
        var lastPeak = -1000
        val threshold = samples.maxOf { abs(it.toInt()) } * 0.4
        for (i in 1 until samples.size - 1) {
            val v = abs(samples[i].toInt())
            if (v > threshold && v > abs(samples[i - 1].toInt()) && v >= abs(samples[i + 1].toInt())) {
                if (i - lastPeak > 50) {
                    peaks++
                    lastPeak = i
                }
            }
        }
        return peaks >= 2
    }
}
