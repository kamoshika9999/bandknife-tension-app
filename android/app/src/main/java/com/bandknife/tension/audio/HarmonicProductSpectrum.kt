package com.bandknife.tension.audio

import kotlin.math.exp
import kotlin.math.ln

/**
 * 調和積スペクトル（HPS）。倍音ピークの積から基音ビンを推定する。
 */
object HarmonicProductSpectrum {
    private const val MAX_HARMONIC_ORDER = 5

    data class Result(
        val frequencyHz: Double,
        val confidence: Double
    )

    fun estimate(
        mags: DoubleArray,
        sampleRate: Int,
        bufferSize: Int,
        minBin: Int,
        maxBin: Int,
        maxHarmonicOrder: Int = MAX_HARMONIC_ORDER
    ): Result? {
        if (maxBin <= minBin) return null

        val size = maxBin - minBin + 1
        val scores = DoubleArray(size) { Double.NEGATIVE_INFINITY }

        for (bin in minBin..maxBin) {
            var logSum = 0.0
            var count = 0
            for (order in 1..maxHarmonicOrder) {
                val harmonicBin = bin * order
                if (harmonicBin >= mags.size) break
                val mag = mags[harmonicBin]
                if (mag <= 0.0) continue
                logSum += ln(mag.coerceAtLeast(1e-12))
                count++
            }
            if (count > 0) {
                scores[bin - minBin] = logSum / count
            }
        }

        var peakIdx = 0
        var peakScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > peakScore) {
                peakScore = scores[i]
                peakIdx = i
            }
        }
        if (!peakScore.isFinite() || peakScore <= -50.0) return null

        val peakBin = minBin + peakIdx
        val refinedBin = FftEngine.parabolicInterpolation(
            padScores(scores, minBin),
            peakBin
        )
        val frequencyHz = refinedBin * sampleRate / bufferSize

        val sorted = scores.filter { it.isFinite() }.sortedDescending()
        val second = sorted.getOrElse(1) { peakScore - 10.0 }
        val confidence = (1.0 - exp(second - peakScore)).coerceIn(0.0, 1.0)
        return Result(frequencyHz, confidence)
    }

    private fun padScores(scores: DoubleArray, minBin: Int): DoubleArray {
        val padded = DoubleArray(minBin + scores.size + 1)
        for (i in scores.indices) {
            padded[minBin + i] = scores[i]
        }
        return padded
    }
}
