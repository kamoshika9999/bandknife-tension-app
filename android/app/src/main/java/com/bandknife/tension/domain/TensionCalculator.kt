package com.bandknife.tension.domain

object TensionCalculator {
    fun tensionNewtons(massPerMeter: Double, spanMeters: Double, frequencyHz: Double): Double {
        if (massPerMeter <= 0 || spanMeters <= 0 || frequencyHz <= 0) return 0.0
        return 4.0 * massPerMeter * spanMeters * spanMeters * frequencyHz * frequencyHz
    }

    fun massFromDimensions(widthM: Double, thicknessM: Double, densityKgM3: Double): Double {
        return widthM * thicknessM * densityKgM3
    }

    fun newtonsToKgf(newtons: Double): Double = newtons / 9.80665

    fun isWithinSpec(value: Double, lower: Double, upper: Double): Boolean = value in lower..upper

    fun deviationFromStandard(value: Double, standard: Double): Double = value - standard
}
