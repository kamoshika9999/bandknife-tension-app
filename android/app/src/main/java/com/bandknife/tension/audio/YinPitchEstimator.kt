package com.bandknife.tension.audio

import kotlin.math.abs

/**
 * YIN 基本周波数推定（Cheveigné & Kawahara, 2002）。
 * 倍音が強い信号でも自己相関ベースで基音周期を推定する。
 */
object YinPitchEstimator {
    const val DEFAULT_THRESHOLD = 0.15
    private const val MAX_UNCERTAIN_CMND = 0.45

    data class Result(
        val frequencyHz: Double,
        val confidence: Double,
        val cmnd: Double
    )

    fun estimate(
        samples: DoubleArray,
        sampleRate: Int,
        minFreq: Double,
        maxFreq: Double,
        threshold: Double = DEFAULT_THRESHOLD
    ): Result? {
        if (samples.size < 4) return null

        val tauMin = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / minFreq).toInt().coerceAtMost(samples.size / 2)
        if (tauMax <= tauMin + 2) return null

        val mean = samples.average()
        val x = DoubleArray(samples.size) { samples[it] - mean }

        val diff = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            val limit = samples.size - tau
            for (j in 0 until limit) {
                val d = x[j] - x[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        val cmnd = DoubleArray(tauMax + 1)
        cmnd[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..tauMax) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum == 0.0) 1.0 else diff[tau] * tau / runningSum
        }

        var tau = tauMin
        while (tau < tauMax && cmnd[tau] >= threshold) tau++

        while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++

        if (tau >= tauMax || cmnd[tau] >= threshold) {
            var bestTau = tauMin
            var bestVal = cmnd[tauMin]
            for (t in (tauMin + 1)..tauMax) {
                if (cmnd[t] < bestVal) {
                    bestVal = cmnd[t]
                    bestTau = t
                }
            }
            if (bestVal >= MAX_UNCERTAIN_CMND) return null
            tau = bestTau
        }

        val refinedTau = parabolicMinimum(cmnd, tau)
        val frequencyHz = sampleRate / refinedTau
        if (frequencyHz < minFreq || frequencyHz > maxFreq) return null

        val confidence = (1.0 - cmnd[tau]).coerceIn(0.0, 1.0)
        return Result(frequencyHz, confidence, cmnd[tau])
    }

    /** 打撃のアタックを除き、減衰振動部分を YIN に渡す。 */
    fun extractRingingSegment(samples: DoubleArray, skipFraction: Double = 0.15): DoubleArray {
        if (samples.isEmpty()) return samples
        val skip = (samples.size * skipFraction).toInt().coerceIn(0, samples.size - 1)
        if (skip >= samples.size - 8) return samples
        return samples.copyOfRange(skip, samples.size)
    }

    private fun parabolicMinimum(values: DoubleArray, index: Int): Double {
        if (index <= 0 || index >= values.size - 1) return index.toDouble()
        val y0 = values[index - 1]
        val y1 = values[index]
        val y2 = values[index + 1]
        val denom = y0 - 2 * y1 + y2
        return if (abs(denom) < 1e-12) index.toDouble() else index + 0.5 * (y0 - y2) / denom
    }
}
