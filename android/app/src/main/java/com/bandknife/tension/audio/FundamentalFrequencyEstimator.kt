package com.bandknife.tension.audio

import kotlin.math.abs

/**
 * スペクトル半分たどり・HPS・YIN を融合して基音周波数を決定する。
 */
object FundamentalFrequencyEstimator {
    private const val AGREEMENT_RATIO = 0.18
    private const val FUNDAMENTAL_BAND_UPPER_HZ = 25.0

    data class Estimate(
        val frequencyHz: Double,
        val method: String
    )

    fun estimate(
        halvingHz: Double,
        mags: DoubleArray,
        waveform: DoubleArray?,
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        minFreq: Double,
        maxFreq: Double
    ): Estimate {
        val candidates = mutableListOf<Triple<Double, Double, String>>()

        if (halvingHz in minFreq..maxFreq) {
            candidates += Triple(halvingHz, 0.25, "halving")
        }

        HarmonicProductSpectrum.estimate(mags, sampleRate, bufferSize, minBin, maxBin)?.let { hps ->
            candidates += Triple(hps.frequencyHz, 0.35 * hps.confidence.coerceAtLeast(0.2), "hps")
        }

        waveform?.let { raw ->
            val ringing = YinPitchEstimator.extractRingingSegment(raw)
            val minSamples = (sampleRate / minFreq * 1.8).toInt()
            if (ringing.size >= minSamples) {
                YinPitchEstimator.estimate(ringing, sampleRate, minFreq, maxFreq)?.let { yin ->
                    candidates += Triple(yin.frequencyHz, 0.4 * yin.confidence.coerceAtLeast(0.15), "yin")
                }
            }
        }

        if (candidates.isEmpty()) {
            return Estimate(halvingHz.coerceIn(minFreq, maxFreq), "halving")
        }
        if (candidates.size == 1) {
            val only = candidates.first()
            return Estimate(only.first, only.third)
        }

        val normalized = candidates.map { (hz, weight, method) ->
            Triple(normalizeToFundamental(hz, minFreq), weight, method)
        }

        val bestGroup = normalized
            .groupBy { (hz, _, _) -> clusterKey(hz) }
            .maxBy { (_, group) -> group.sumOf { it.second } }
            .value

        val totalWeight = bestGroup.sumOf { it.second }.coerceAtLeast(1e-6)
        val fusedHz = bestGroup.sumOf { it.first * it.second } / totalWeight
        val methods = bestGroup.joinToString("+") { it.third }
        return Estimate(fusedHz.coerceIn(minFreq, maxFreq), methods)
    }

    /** 倍音ピーク（例: 85 Hz）を基音帯域（〜25 Hz）へ折りたたむ。 */
    internal fun normalizeToFundamental(hz: Double, minFreq: Double): Double {
        var f = hz
        while (f > FUNDAMENTAL_BAND_UPPER_HZ && f / 2.0 >= minFreq) {
            f /= 2.0
        }
        return f
    }

    internal fun agree(a: Double, b: Double): Boolean {
        if (a <= 0.0 || b <= 0.0) return false
        val ratio = a / b
        return abs(ratio - 1.0) <= AGREEMENT_RATIO ||
            abs(ratio - 2.0) <= AGREEMENT_RATIO ||
            abs(ratio - 0.5) <= AGREEMENT_RATIO
    }

    private fun clusterKey(hz: Double): Int {
        return (hz * 10).toInt()
    }
}
