package com.bandknife.tension.domain

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class MeasurementStats(
    val values: List<Double>,
    val outliers: List<Double> = emptyList(),
    val mean: Double = 0.0,
    val median: Double = 0.0,
    val stdDev: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val range: Double = 0.0,
    val cvPercent: Double = 0.0,
    val ci95Lower: Double = 0.0,
    val ci95Upper: Double = 0.0,
    val ci95Margin: Double = 0.0
)

object StatisticsEngine {
    fun analyze(values: List<Double>, removeOutliers: Boolean = true): MeasurementStats {
        if (values.isEmpty()) return MeasurementStats(emptyList())
        val cleaned = if (removeOutliers && values.size >= 3) removeGrubbsOutliers(values) else values
        if (cleaned.isEmpty()) return MeasurementStats(values, values - values.toSet())

        val mean = cleaned.average()
        val median = cleaned.sorted().let {
            val mid = it.size / 2
            if (it.size % 2 == 0) (it[mid - 1] + it[mid]) / 2.0 else it[mid]
        }
        val variance = if (cleaned.size > 1) cleaned.map { (it - mean).pow(2) }.average() else 0.0
        val stdDev = sqrt(variance)
        val min = cleaned.min()
        val max = cleaned.max()
        val cv = if (mean != 0.0) (stdDev / mean) * 100.0 else 0.0
        val margin = if (cleaned.size > 1) tCritical95(cleaned.size - 1) * stdDev / sqrt(cleaned.size.toDouble()) else 0.0

        return MeasurementStats(
            values = cleaned,
            outliers = values.filter { it !in cleaned },
            mean = mean,
            median = median,
            stdDev = stdDev,
            min = min,
            max = max,
            range = max - min,
            cvPercent = cv,
            ci95Lower = mean - margin,
            ci95Upper = mean + margin,
            ci95Margin = margin
        )
    }

    fun linearTrend(values: List<Pair<Long, Double>>): Pair<Double, Double>? {
        if (values.size < 2) return null
        val n = values.size.toDouble()
        val sumX = values.sumOf { it.first.toDouble() }
        val sumY = values.sumOf { it.second }
        val sumXY = values.sumOf { it.first * it.second }
        val sumX2 = values.sumOf { it.first.toDouble().pow(2) }
        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        return slope to intercept
    }

    fun predictCrossingTime(slopePerMs: Double, intercept: Double, threshold: Double, lastTimeMs: Long): Long? {
        if (slopePerMs >= 0) return null
        val x = (threshold - intercept) / slopePerMs
        return if (x > lastTimeMs) x.toLong() else null
    }

    fun cpk(mean: Double, stdDev: Double, lower: Double, upper: Double): Pair<Double, Double> {
        if (stdDev <= 0) return 0.0 to 0.0
        val cp = (upper - lower) / (6 * stdDev)
        val cpu = (upper - mean) / (3 * stdDev)
        val cpl = (mean - lower) / (3 * stdDev)
        return cp to minOf(cpu, cpl)
    }

    private fun removeGrubbsOutliers(values: List<Double>): List<Double> {
        var current = values.toMutableList()
        while (current.size >= 3) {
            val mean = current.average()
            val std = sqrt(current.map { (it - mean).pow(2) }.average())
            if (std == 0.0) break
            val maxDev = current.maxOf { abs(it - mean) }
            val g = maxDev / std
            val critical = grubbsCritical(current.size)
            if (g > critical) {
                current.remove(maxDev)
            } else break
        }
        return current
    }

    private fun grubbsCritical(n: Int): Double {
        val t = tCritical95(n - 2)
        return ((n - 1) / sqrt(n.toDouble())) * sqrt(t * t / (n - 2 + t * t))
    }

    private fun tCritical95(df: Int): Double {
        if (df <= 0) return 12.706
        val table = mapOf(
            1 to 12.706, 2 to 4.303, 3 to 3.182, 4 to 2.776, 5 to 2.571,
            6 to 2.447, 7 to 2.365, 8 to 2.306, 9 to 2.262, 10 to 2.228,
            15 to 2.131, 20 to 2.086, 25 to 2.060, 30 to 2.042, 40 to 2.021,
            60 to 2.000, 120 to 1.980
        )
        return table[df] ?: 1.96
    }

    fun histogram(values: List<Double>, bins: Int = 8): List<Pair<Double, Int>> {
        if (values.isEmpty()) return emptyList()
        val min = values.min()
        val max = values.max()
        val range = max(max - min, 0.001)
        val binWidth = range / bins
        return (0 until bins).map { i ->
            val center = min + binWidth * (i + 0.5)
            val count = values.count { v ->
                if (i == bins - 1) v >= min + binWidth * i && v <= max
                else v >= min + binWidth * i && v < min + binWidth * (i + 1)
            }
            center to count
        }
    }

    fun normalPdf(x: Double, mean: Double, stdDev: Double): Double {
        if (stdDev <= 0) return 0.0
        val z = (x - mean) / stdDev
        return (1.0 / (stdDev * sqrt(2 * Math.PI))) * exp(-0.5 * z * z)
    }

    private fun exp(x: Double) = kotlin.math.exp(x)
}
