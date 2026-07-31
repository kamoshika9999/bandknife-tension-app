package com.bandknife.tension

import com.bandknife.tension.audio.AudioAnalyzer
import com.bandknife.tension.audio.FundamentalFrequencyEstimator
import com.bandknife.tension.audio.YinPitchEstimator
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class YinPitchEstimatorTest {
    private val sampleRate = AudioAnalyzer.SAMPLE_RATE

    @Test
    fun `純音 7Hz を推定する`() {
        val waveform = sineWave(7.0, durationSec = 1.0)
        val result = YinPitchEstimator.estimate(waveform, sampleRate, 5.0, 300.0)
        assertTrue("expected ~7 Hz but was ${result?.frequencyHz}", abs(result!!.frequencyHz - 7.0) < 0.8)
    }

    @Test
    fun `12次倍音が強くても基音 7Hz を推定する`() {
        val waveform = harmonicWaveform(
            fundamentalHz = 7.0,
            durationSec = 1.2,
            weights = (1..12).map { order ->
                order to if (order == 12) 1.0 else 0.15 + 0.03 * order
            }
        )
        val ringing = YinPitchEstimator.extractRingingSegment(waveform, skipFraction = 0.1)
        val result = YinPitchEstimator.estimate(ringing, sampleRate, 5.0, 20.0)
        assertTrue("expected ~7 Hz but was ${result?.frequencyHz}", abs(result!!.frequencyHz - 7.0) < 1.2)
    }

    @Test
    fun normalizeToFundamental_foldsHighHarmonicPeak() {
        val folded = FundamentalFrequencyEstimator.normalizeToFundamental(88.0, 5.0)
        assertTrue(abs(folded - 11.0) < 0.1)
    }

    private fun sineWave(freqHz: Double, durationSec: Double): DoubleArray {
        val n = (sampleRate * durationSec).toInt()
        return DoubleArray(n) { i ->
            sin(2.0 * PI * freqHz * i / sampleRate)
        }
    }

    private fun harmonicWaveform(
        fundamentalHz: Double,
        durationSec: Double,
        weights: List<Pair<Int, Double>>
    ): DoubleArray {
        val n = (sampleRate * durationSec).toInt()
        return DoubleArray(n) { i ->
            val t = i / sampleRate.toDouble()
            weights.sumOf { (order, weight) ->
                weight * sin(2.0 * PI * fundamentalHz * order * t)
            }
        }
    }
}
