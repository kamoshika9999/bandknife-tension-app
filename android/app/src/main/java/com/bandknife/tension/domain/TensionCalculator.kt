package com.bandknife.tension.domain

import com.bandknife.tension.data.EquipmentEntity
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 張力と固有振動数の換算。
 *
 * 弦のみ（旧式）: T = 4·m·S²·f²
 *
 * 張力＋曲げ（両端支持のオイラー・ベルヌーイ梁）:
 *   ω² = (nπ/S)² · T/m + (nπ/S)⁴ · EI/m
 *   T = m · (ω² − (nπ/S)⁴·EI/m) / (nπ/S)²
 *
 * 断面二次モーメント I:
 *   - 面内曲げ（刃幅方向・耳に聞こえやすい）: I = t·w³/12
 *   - 面外曲げ（板厚方向）: I = w·t³/12
 */
object TensionCalculator {
    const val DEFAULT_SPAN_METERS = 1.0

    /** 弦振動のみ（幅・厚み・曲げ剛性が未設定のときのフォールバック） */
    fun tensionNewtonsString(massPerMeter: Double, spanMeters: Double, frequencyHz: Double): Double {
        if (massPerMeter <= 0 || spanMeters <= 0 || frequencyHz <= 0) return 0.0
        return 4.0 * massPerMeter * spanMeters * spanMeters * frequencyHz * frequencyHz
    }

    fun frequencyHzString(massPerMeter: Double, spanMeters: Double, tensionN: Double): Double {
        if (massPerMeter <= 0 || spanMeters <= 0 || tensionN <= 0) return 0.0
        return sqrt(tensionN / (4.0 * massPerMeter * spanMeters * spanMeters))
    }

    /** @deprecated 互換のため残す。新規は [tensionFromEquipment] を使う。 */
    fun tensionNewtons(massPerMeter: Double, spanMeters: Double, frequencyHz: Double): Double =
        tensionNewtonsString(massPerMeter, spanMeters, frequencyHz)

    /** @deprecated 互換のため残す。 */
    fun frequencyHz(massPerMeter: Double, spanMeters: Double, tensionN: Double): Double =
        frequencyHzString(massPerMeter, spanMeters, tensionN)

    fun secondMomentInPlane(widthM: Double, thicknessM: Double): Double =
        thicknessM * widthM.pow(3) / 12.0

    fun secondMomentOutOfPlane(widthM: Double, thicknessM: Double): Double =
        widthM * thicknessM.pow(3) / 12.0

    fun flexuralRigidity(
        widthM: Double,
        thicknessM: Double,
        youngModulusPa: Double,
        edgewiseBending: Boolean
    ): Double {
        if (widthM <= 0 || thicknessM <= 0 || youngModulusPa <= 0) return 0.0
        val inertia = if (edgewiseBending) {
            secondMomentInPlane(widthM, thicknessM)
        } else {
            secondMomentOutOfPlane(widthM, thicknessM)
        }
        return youngModulusPa * inertia
    }

    fun massFromDimensions(widthM: Double, thicknessM: Double, densityKgM3: Double): Double =
        widthM * thicknessM * densityKgM3

    fun tensionNewtonsBeam(
        massPerMeter: Double,
        spanMeters: Double,
        frequencyHz: Double,
        flexuralRigidity: Double,
        mode: Int = 1
    ): Double {
        if (massPerMeter <= 0 || spanMeters <= 0 || frequencyHz <= 0 || mode <= 0) return 0.0
        val waveNumber = mode * PI / spanMeters
        val omegaSq = (2.0 * PI * frequencyHz).pow(2)
        val bendingTerm = waveNumber.pow(4) * flexuralRigidity / massPerMeter
        if (omegaSq <= bendingTerm) return 0.0
        return massPerMeter * (omegaSq - bendingTerm) / waveNumber.pow(2)
    }

    fun frequencyHzBeam(
        massPerMeter: Double,
        spanMeters: Double,
        tensionN: Double,
        flexuralRigidity: Double,
        mode: Int = 1
    ): Double {
        if (massPerMeter <= 0 || spanMeters <= 0 || tensionN < 0 || mode <= 0) return 0.0
        val waveNumber = mode * PI / spanMeters
        val omegaSq = waveNumber.pow(2) * tensionN / massPerMeter +
            waveNumber.pow(4) * flexuralRigidity / massPerMeter
        if (omegaSq <= 0) return 0.0
        return sqrt(omegaSq) / (2.0 * PI)
    }

    fun tensionFromEquipment(equipment: EquipmentEntity, frequencyHz: Double): Double {
        val rigidity = equipment.beamFlexuralRigidity()
        return if (rigidity != null && rigidity > 0) {
            tensionNewtonsBeam(
                equipment.massPerMeter,
                equipment.spanMeters,
                frequencyHz,
                rigidity,
                equipment.vibrationMode
            )
        } else {
            tensionNewtonsString(equipment.massPerMeter, equipment.spanMeters, frequencyHz)
        }
    }

    fun frequencyFromEquipment(equipment: EquipmentEntity, tensionN: Double): Double {
        val rigidity = equipment.beamFlexuralRigidity()
        return if (rigidity != null && rigidity > 0) {
            frequencyHzBeam(
                equipment.massPerMeter,
                equipment.spanMeters,
                tensionN,
                rigidity,
                equipment.vibrationMode
            )
        } else {
            frequencyHzString(equipment.massPerMeter, equipment.spanMeters, tensionN)
        }
    }

    fun newtonsToKgf(newtons: Double): Double = newtons / 9.80665

    fun isWithinSpec(value: Double, lower: Double, upper: Double): Boolean =
        lower > 0 && upper > lower && value in lower..upper

    fun deviationFromStandard(value: Double, standard: Double): Double = value - standard
}

fun EquipmentEntity.beamFlexuralRigidity(): Double? {
    if (widthMm <= 0 || thicknessMm <= 0 || youngModulusGpa <= 0) return null
    return TensionCalculator.flexuralRigidity(
        widthMm / 1000.0,
        thicknessMm / 1000.0,
        youngModulusGpa * 1_000_000_000.0,
        edgewiseBending
    )
}
