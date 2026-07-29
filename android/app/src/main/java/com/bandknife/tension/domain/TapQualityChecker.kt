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
    const val WEAK_RATIO = 0.15
    const val STRONG_RATIO = 0.95
    private const val HARMONIC_RATIO = 0.6

    fun formatPercent(normalized: Double): Int =
        (normalized * 100).toInt().coerceIn(0, 100)

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
        val pct = formatPercent(normalized)
        val minPct = formatPercent(WEAK_RATIO)
        val maxPct = formatPercent(STRONG_RATIO)
        val message = when (quality) {
            TapQuality.GOOD -> "打撃レベル ${pct}%（適正 ${minPct}〜${maxPct}%）"
            TapQuality.TOO_WEAK -> "打撃レベル ${pct}%（目標 ${minPct}% 以上）"
            TapQuality.TOO_STRONG -> "打撃レベル ${pct}%（${maxPct}% 以下に調整）"
            TapQuality.DOUBLE_HIT -> "打撃レベル ${pct}% — 跳ね返りで2回当たっています。軽く弾くように叩いてください"
            TapQuality.HARMONIC_DOMINANT -> "打撃レベル ${pct}% — スパンの中央を樹脂の柄などで叩いてください"
        }
        return TapAnalysis(quality, peakFrequency, normalized, message)
    }
}
