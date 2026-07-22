package com.bandknife.tension.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

object FftEngine {
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return
        val evenR = DoubleArray(n / 2)
        val evenI = DoubleArray(n / 2)
        val oddR = DoubleArray(n / 2)
        val oddI = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            evenR[i] = real[2 * i]
            evenI[i] = imag[2 * i]
            oddR[i] = real[2 * i + 1]
            oddI[i] = imag[2 * i + 1]
        }
        fft(evenR, evenI)
        fft(oddR, oddI)
        for (k in 0 until n / 2) {
            val angle = -2.0 * PI * k / n
            val tR = cos(angle) * oddR[k] - kotlin.math.sin(angle) * oddI[k]
            val tI = cos(angle) * oddI[k] + kotlin.math.sin(angle) * oddR[k]
            real[k] = evenR[k] + tR
            imag[k] = evenI[k] + tI
            real[k + n / 2] = evenR[k] - tR
            imag[k + n / 2] = evenI[k] - tI
        }
    }

    fun magnitudes(real: DoubleArray, imag: DoubleArray): DoubleArray {
        return DoubleArray(real.size / 2) { i ->
            sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
    }

    fun parabolicInterpolation(mags: DoubleArray, peakIndex: Int): Double {
        if (peakIndex <= 0 || peakIndex >= mags.size - 1) return peakIndex.toDouble()
        val y0 = mags[peakIndex - 1]
        val y1 = mags[peakIndex]
        val y2 = mags[peakIndex + 1]
        val denom = y0 - 2 * y1 + y2
        if (denom == 0.0) return peakIndex.toDouble()
        return peakIndex + 0.5 * (y0 - y2) / denom
    }

    fun hammingWindow(size: Int): DoubleArray {
        return DoubleArray(size) { i ->
            0.54 - 0.46 * cos(2.0 * PI * i / (size - 1))
        }
    }
}
