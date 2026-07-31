package com.bandknife.tension

import com.bandknife.tension.audio.AudioAnalyzer
import com.bandknife.tension.audio.FundamentalFrequencyEstimator
import com.bandknife.tension.audio.HarmonicProductSpectrum
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class FundamentalFrequencyEstimatorTest {
    private val sampleRate = AudioAnalyzer.SAMPLE_RATE
    private val bufferSize = AudioAnalyzer.BUFFER_SIZE
    private val minBin = (AudioAnalyzer.MIN_FREQ * bufferSize / sampleRate).toInt().coerceAtLeast(1)
    private val maxBin = (AudioAnalyzer.MAX_FREQ * bufferSize / sampleRate).toInt()

    @Test
    fun `HPS は 12次優勢の倍音列から基音を推定する`() {
        val fundamentalHz = 7.5
        val fundamentalBin = (fundamentalHz * bufferSize / sampleRate).toInt().coerceAtLeast(1)
        val mags = DoubleArray(bufferSize / 2)
        for (order in 1..14) {
            val bin = fundamentalBin * order
            if (bin < mags.size) {
                mags[bin] = if (order == 12) 1.0 else 0.2
            }
        }

        val hps = HarmonicProductSpectrum.estimate(
            mags, sampleRate, bufferSize, minBin, maxBin
        )
        assertTrue("expected ~$fundamentalHz Hz but was ${hps?.frequencyHz}", abs(hps!!.frequencyHz - fundamentalHz) < 1.5)
    }

    @Test
    fun `半分たどりが高周波で止まっても融合で基音を選ぶ`() {
        val fundamentalHz = 7.5
        val fundamentalBin = (fundamentalHz * bufferSize / sampleRate).toInt().coerceAtLeast(1)
        val mags = DoubleArray(bufferSize / 2)
        for (order in 1..14) {
            val bin = fundamentalBin * order
            if (bin < mags.size) {
                mags[bin] = if (order == 12) 1.0 else 0.2
            }
        }

        val halvingHz = fundamentalHz * 12.0
        val waveform = harmonicWaveform(fundamentalHz, durationSec = 1.0)
        val estimate = FundamentalFrequencyEstimator.estimate(
            halvingHz = halvingHz,
            mags = mags,
            waveform = waveform,
            sampleRate = sampleRate,
            bufferSize = bufferSize,
            minBin = minBin,
            maxBin = maxBin,
            minFreq = AudioAnalyzer.MIN_FREQ,
            maxFreq = AudioAnalyzer.MAX_FREQ
        )

        assertTrue(
            "expected ~$fundamentalHz Hz but was ${estimate.frequencyHz} via ${estimate.method}",
            abs(estimate.frequencyHz - fundamentalHz) < 2.0
        )
    }

    private fun harmonicWaveform(fundamentalHz: Double, durationSec: Double): DoubleArray {
        val n = (sampleRate * durationSec).toInt()
        return DoubleArray(n) { i ->
            val t = i / sampleRate.toDouble()
            (1..12).sumOf { order ->
                val weight = if (order == 12) 1.0 else 0.2
                weight * sin(2.0 * PI * fundamentalHz * order * t)
            }
        }
    }
}
