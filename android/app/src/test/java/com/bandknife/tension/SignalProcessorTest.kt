package com.bandknife.tension

import com.bandknife.tension.audio.AudioAnalyzer
import com.bandknife.tension.audio.SignalProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SignalProcessorTest {
    private val sampleRate = AudioAnalyzer.SAMPLE_RATE
    private val bufferSize = AudioAnalyzer.BUFFER_SIZE
    private val minBin = (AudioAnalyzer.MIN_FREQ * bufferSize / sampleRate).toInt()
    private val maxBin = (AudioAnalyzer.MAX_FREQ * bufferSize / sampleRate).toInt()

    @Test
    fun `打撃スペクトルの平均から周波数を推定する`() {
        val processor = SignalProcessor()
        val targetHz = 85.0
        val peakBin = (targetHz * bufferSize / sampleRate).toInt()

        processor.beginTap(1)
        repeat(5) {
            val mags = gaussianPeakMags(peakBin, spread = 1)
            val cleaned = processor.subtractNoise(mags, minBin, maxBin)
            processor.accumulateTapSpectrum(cleaned, minBin, maxBin)
        }
        processor.endTap()

        val freq = processor.frequencyFromFinalizedTap(1, sampleRate, bufferSize, minBin, maxBin)
        assertNotNull(freq)
        assertTrue("expected ~$targetHz Hz but was $freq", abs(freq!! - targetHz) < 3.0)
    }

    @Test
    fun `倍音ピークより低い基本周波数を優先する`() {
        val processor = SignalProcessor()
        val fundamentalHz = 85.0
        val fundamentalBin = (fundamentalHz * bufferSize / sampleRate).toInt()
        val harmonicBin = fundamentalBin * 2

        val mags = DoubleArray(bufferSize / 2)
        mags[fundamentalBin] = 0.5
        mags[harmonicBin] = 1.0

        val (peakIdx, _) = processor.findPeak(mags, minBin, maxBin)
        val freq = processor.frequencyFromPeak(peakIdx, mags, sampleRate, bufferSize)

        assertTrue(abs(freq - fundamentalHz) < 3.0)
    }

    @Test
    fun resolvesHighHarmonicToFundamental() {
        val processor = SignalProcessor()
        val fundamentalHz = 7.0
        val fundamentalBin = (fundamentalHz * bufferSize / sampleRate).toInt().coerceAtLeast(1)
        val harmonicBin = fundamentalBin * 12

        val mags = DoubleArray(bufferSize / 2)
        for (order in 1..12) {
            val bin = fundamentalBin * order
            if (bin < mags.size) mags[bin] = 0.35 + 0.05 * order
        }
        mags[harmonicBin] = 1.0

        val (peakIdx, _) = processor.findPeak(mags, minBin.coerceAtLeast(1), maxBin)
        val freq = processor.frequencyFromPeak(peakIdx, mags, sampleRate, bufferSize)
        assertTrue("expected ~$fundamentalHz Hz but was $freq", abs(freq - fundamentalHz) < 6.0)
    }

    private fun gaussianPeakMags(peakBin: Int, spread: Int): DoubleArray {
        val mags = DoubleArray(bufferSize / 2)
        for (i in (peakBin - spread)..(peakBin + spread)) {
            if (i in mags.indices) {
                val d = (i - peakBin).toDouble()
                mags[i] = kotlin.math.exp(-d * d)
            }
        }
        return mags
    }
}
