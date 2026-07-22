package com.bandknife.tension.domain

enum class TapQuality {
    GOOD, TOO_WEAK, TOO_STRONG, DOUBLE_HIT, HARMONIC_DOMINANT
}

data class TapAnalysis(
    val quality: TapQuality,
    val frequencyHz: Double,
    val amplitude: Double,
    val message: String
)

object TapQualityChecker {
    private const val WEAK_RATIO = 0.15
    private const val STRONG_RATIO = 0.95
    private const val HARMONIC_RATIO = 0.6

    fun analyze(
        samples: ShortArray,
        peakFrequency: Double,
        fundamentalMagnitude: Double,
        harmonicMagnitude: Double,
        maxAmplitude: Double,
        hadDoublePeak: Boolean
    ): TapAnalysis {
        val normalized = maxAmplitude / Short.MAX_VALUE.toDouble()
        val quality = when {
            hadDoublePeak -> TapQuality.DOUBLE_HIT
            normalized < WEAK_RATIO -> TapQuality.TOO_WEAK
            normalized > STRONG_RATIO -> TapQuality.TOO_STRONG
            harmonicMagnitude > fundamentalMagnitude * HARMONIC_RATIO -> TapQuality.HARMONIC_DOMINANT
            else -> TapQuality.GOOD
        }
        val message = when (quality) {
            TapQuality.GOOD -> "適正な打撃です"
            TapQuality.TOO_WEAK -> "もう少し強く叩いてください"
            TapQuality.TOO_STRONG -> "叩く力を弱めるか、マイクを少し離してください"
            TapQuality.DOUBLE_HIT -> "跳ね返りで2回当たっています。軽く弾くように叩いてください"
            TapQuality.HARMONIC_DOMINANT -> "スパンの中央を樹脂の柄などで叩いてください"
        }
        return TapAnalysis(quality, peakFrequency, normalized, message)
    }
}
