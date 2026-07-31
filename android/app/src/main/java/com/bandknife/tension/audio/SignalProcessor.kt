package com.bandknife.tension.audio

import com.bandknife.tension.audio.FftEngine.parabolicInterpolation
import kotlin.math.log10
import kotlin.math.max

/**
 * 電波望遠鏡のオン・オフ差分・積分に相当する信号処理。
 * - 測定前のノイズスペクトルを記録し、各打撃スペクトルから差し引く
 * - 複数打撃のスペクトルを平均して SNR を √N 改善する
 */
class SignalProcessor {
    private var noiseCalibrationActive = false
    private var noiseAccum: DoubleArray? = null
    private var noiseAccumCount = 0
    private var noiseSpectrum: DoubleArray? = null

    private var tapSpectrumAccum: DoubleArray? = null
    private var tapSpectrumCount = 0
    private var activeTapId = 0L

    private val tapsToStack = mutableSetOf<Long>()
    private val finalizedTapSpectra = mutableMapOf<Long, DoubleArray>()
    private val finalizedTapWaveforms = mutableMapOf<Long, DoubleArray>()
    private val stackedSpectra = mutableListOf<DoubleArray>()
    private var stackedSpectrum: DoubleArray? = null
    private var tapWaveformSamples: MutableList<Double>? = null
    private var noiseWaveformSamples: MutableList<Double>? = null

    companion object {
        private const val MAX_TAP_WAVEFORM_SAMPLES = 44100 * 2
        private const val MAX_NOISE_WAVEFORM_SAMPLES = 44100 * 6
    }

    val isNoiseCalibrating: Boolean get() = noiseCalibrationActive

    fun beginNoiseCalibration() {
        noiseCalibrationActive = true
        noiseAccum = null
        noiseAccumCount = 0
        noiseSpectrum = null
        noiseWaveformSamples = mutableListOf()
    }

    fun finalizeNoiseCalibration(): Double {
        noiseCalibrationActive = false
        if (noiseAccumCount > 0 && noiseAccum != null) {
            noiseSpectrum = DoubleArray(noiseAccum!!.size) { noiseAccum!![it] / noiseAccumCount }
        }
        val avg = noiseSpectrum?.average() ?: 0.0
        return 20.0 * log10(avg.coerceAtLeast(1e-10))
    }

    fun resetTapAccumulation() {
        tapSpectrumAccum = null
        tapSpectrumCount = 0
        activeTapId = 0
        tapWaveformSamples = null
        tapsToStack.clear()
        finalizedTapSpectra.clear()
        finalizedTapWaveforms.clear()
        stackedSpectra.clear()
        stackedSpectrum = null
    }

    fun beginTap(tapId: Long) {
        if (tapId != activeTapId) {
            finalizeCurrentTapSpectrum()
            activeTapId = tapId
            tapSpectrumAccum = null
            tapSpectrumCount = 0
            tapWaveformSamples = mutableListOf()
        }
    }

    fun accumulateTapWaveform(samples: ShortArray, count: Int) {
        val buffer = tapWaveformSamples ?: return
        val limit = minOf(count, MAX_TAP_WAVEFORM_SAMPLES - buffer.size)
        for (i in 0 until limit) {
            buffer.add(samples[i].toDouble())
        }
    }

    fun endTap() {
        finalizeCurrentTapSpectrum()
        activeTapId = 0
    }

    fun markTapForStacking(tapId: Long) {
        tapsToStack.add(tapId)
        finalizedTapSpectra[tapId]?.let { addToStack(it) }
    }

    fun accumulateNoise(mags: DoubleArray, minBin: Int, maxBin: Int) {
        if (!noiseCalibrationActive) return
        val accum = ensureBandArray(noiseAccum, minBin, maxBin)
        noiseAccum = accum
        for (i in minBin..maxBin) accum[i - minBin] += mags[i]
        noiseAccumCount++
    }

    fun accumulateNoiseWaveform(samples: ShortArray, count: Int) {
        if (!noiseCalibrationActive) return
        val buffer = noiseWaveformSamples ?: return
        val limit = minOf(count, MAX_NOISE_WAVEFORM_SAMPLES - buffer.size)
        for (i in 0 until limit) {
            buffer.add(samples[i].toDouble())
        }
    }

    fun snapshotNoiseWaveform(): DoubleArray? =
        noiseWaveformSamples?.toDoubleArray()?.takeIf { it.isNotEmpty() }

    fun snapshotNoiseSpectrum(): DoubleArray? = noiseSpectrum?.copyOf()

    fun snapshotTapWaveform(tapId: Long): DoubleArray? =
        finalizedTapWaveforms[tapId]?.copyOf()

    fun snapshotTapSpectrum(tapId: Long): DoubleArray? =
        finalizedTapSpectra[tapId]?.copyOf()

    fun subtractNoise(mags: DoubleArray, minBin: Int, maxBin: Int): DoubleArray {
        val noise = noiseSpectrum ?: return mags
        val out = mags.copyOf()
        for (i in minBin..maxBin) {
            val idx = i - minBin
            if (idx < noise.size) out[i] = max(0.0, mags[i] - noise[idx])
        }
        return out
    }

    fun accumulateTapSpectrum(cleanedMags: DoubleArray, minBin: Int, maxBin: Int) {
        val accum = ensureBandArray(tapSpectrumAccum, minBin, maxBin)
        tapSpectrumAccum = accum
        for (i in minBin..maxBin) accum[i - minBin] += cleanedMags[i]
        tapSpectrumCount++
    }

    fun findPeak(mags: DoubleArray, minBin: Int, maxBin: Int): Pair<Int, Double> {
        var peakIdx = minBin
        var peakMag = 0.0
        for (i in minBin..maxBin) {
            if (mags[i] > peakMag) {
                peakMag = mags[i]
                peakIdx = i
            }
        }
        val fundamentalBin = resolveFundamentalBin(peakIdx, mags, minBin, peakMag)
        return fundamentalBin to mags[fundamentalBin]
    }

    /**
     * 叩いた音は基本周波数より倍音が強く出ることが多い。
     * ピークが倍音のときは、下位の成分が十分あれば半分の周波数へたどる。
     */
    internal fun resolveFundamentalBin(
        peakBin: Int,
        mags: DoubleArray,
        minBin: Int,
        referenceMag: Double,
        threshold: Double = 0.35
    ): Int {
        var fundamental = peakBin
        var current = peakBin
        while (true) {
            val half = current / 2
            if (half < minBin) break
            if (mags[half] > referenceMag * threshold) {
                fundamental = half
                current = half
            } else {
                break
            }
        }
        return fundamental
    }

    fun frequencyFromFinalizedTap(
        tapId: Long,
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double
    ): Double? {
        val spectrum = finalizedTapSpectra[tapId] ?: return null
        return frequencyFromBandSpectrum(
            spectrum,
            finalizedTapWaveforms[tapId],
            sampleRate,
            bufferSize,
            minBin,
            maxBin,
            minFreq,
            maxFreq
        )
    }

    fun frequencyFromAccumulatedTap(
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double
    ): Double? {
        val accum = tapSpectrumAccum ?: return null
        if (tapSpectrumCount <= 0) return null
        val averaged = DoubleArray(accum.size) { accum[it] / tapSpectrumCount }
        val waveform = tapWaveformSamples?.toDoubleArray()
        return frequencyFromBandSpectrum(
            averaged,
            waveform,
            sampleRate,
            bufferSize,
            minBin,
            maxBin,
            minFreq,
            maxFreq
        )
    }

    fun frequencyFromPeak(peakIdx: Int, mags: DoubleArray, sampleRate: Int, bufferSize: Int): Double {
        val refinedBin = parabolicInterpolation(mags, peakIdx)
        return refinedBin * sampleRate / bufferSize
    }

    fun stackedFrequency(
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double
    ): Double? {
        val stacked = stackedSpectrum ?: return null
        if (stacked.size < 2) return null
        var peakIdx = 0
        var peakMag = 0.0
        for (i in stacked.indices) {
            if (stacked[i] > peakMag) {
                peakMag = stacked[i]
                peakIdx = i
            }
        }
        if (peakMag <= 0.0) return null
        val padded = DoubleArray(maxBin + 2)
        for (i in stacked.indices) padded[minBin + i] = stacked[i]
        return resolveFundamentalFrequency(
            padded,
            waveform = null,
            sampleRate,
            bufferSize,
            minBin,
            maxBin,
            minFreq,
            maxFreq,
            peakBin = minBin + peakIdx,
            peakMag = peakMag
        )
    }

    fun toDisplaySpectrum(mags: DoubleArray, minBin: Int, maxBin: Int, points: Int = 64): FloatArray {
        val source = stackedSpectrum ?: extractBand(mags, minBin, maxBin)
        return FloatArray(points) { i ->
            val bin = (source.size - 1) * i / (points - 1).coerceAtLeast(1)
            source[bin.coerceIn(0, source.size - 1)].toFloat()
        }
    }

    fun estimateSignalQuality(peakMag: Double): Double {
        val noise = noiseSpectrum
        val floor = if (noise != null && noise.isNotEmpty()) {
            noise.average().coerceAtLeast(1e-10)
        } else {
            1e-10
        }
        val snr = peakMag / floor
        // 10:1 → 50%, 100:1 → 100% 程度にマッピング
        return (log10(snr.coerceAtLeast(1.0)) / 2.0 * 100.0).coerceIn(0.0, 100.0)
    }

    val stackedTapCount: Int get() = stackedSpectra.size
    val hasNoiseProfile: Boolean get() = noiseSpectrum != null

    private fun finalizeCurrentTapSpectrum() {
        if (tapSpectrumCount <= 0 || tapSpectrumAccum == null || activeTapId == 0L) return
        val averaged = DoubleArray(tapSpectrumAccum!!.size) { tapSpectrumAccum!![it] / tapSpectrumCount }
        finalizedTapSpectra[activeTapId] = averaged
        tapWaveformSamples?.let { samples ->
            if (samples.isNotEmpty()) {
                finalizedTapWaveforms[activeTapId] = samples.toDoubleArray()
            }
        }
        if (activeTapId in tapsToStack) addToStack(averaged)
        tapSpectrumAccum = null
        tapSpectrumCount = 0
        tapWaveformSamples = null
    }

    private fun addToStack(spectrum: DoubleArray) {
        stackedSpectra.add(spectrum)
        val n = stackedSpectra.size
        val size = spectrum.size
        stackedSpectrum = DoubleArray(size) { bin ->
            stackedSpectra.sumOf { it[bin] } / n
        }
    }

    private fun ensureBandArray(existing: DoubleArray?, minBin: Int, maxBin: Int): DoubleArray {
        val size = maxBin - minBin + 1
        return existing ?: DoubleArray(size)
    }

    private fun extractBand(mags: DoubleArray, minBin: Int, maxBin: Int): DoubleArray {
        return DoubleArray(maxBin - minBin + 1) { i -> mags[minBin + i] }
    }

    private fun frequencyFromBandSpectrum(
        bandSpectrum: DoubleArray,
        waveform: DoubleArray?,
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double
    ): Double {
        var peakRelIdx = 0
        var peakMag = 0.0
        for (i in bandSpectrum.indices) {
            if (bandSpectrum[i] > peakMag) {
                peakMag = bandSpectrum[i]
                peakRelIdx = i
            }
        }
        val padded = DoubleArray(maxBin + 2)
        for (i in bandSpectrum.indices) padded[minBin + i] = bandSpectrum[i]
        return resolveFundamentalFrequency(
            padded,
            waveform,
            sampleRate,
            bufferSize,
            minBin,
            maxBin,
            minFreq,
            maxFreq,
            peakBin = minBin + peakRelIdx,
            peakMag = peakMag
        )
    }

    private fun resolveFundamentalFrequency(
        mags: DoubleArray,
        waveform: DoubleArray?,
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double,
        peakBin: Int,
        peakMag: Double
    ): Double {
        val halvingBin = resolveFundamentalBin(peakBin, mags, minBin, peakMag)
        val halvingHz = frequencyFromPeak(halvingBin, mags, sampleRate, bufferSize)
        return FundamentalFrequencyEstimator.estimate(
            halvingHz = halvingHz,
            mags = mags,
            waveform = waveform,
            sampleRate = sampleRate,
            bufferSize = bufferSize,
            minBin = minBin,
            maxBin = maxBin,
            minFreq = minFreq,
            maxFreq = maxFreq
        ).frequencyHz
    }
}
