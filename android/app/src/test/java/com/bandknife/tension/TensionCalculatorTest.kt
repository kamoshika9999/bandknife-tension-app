package com.bandknife.tension

import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.domain.beamFlexuralRigidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TensionCalculatorTest {
    private val defaultEquipment = EquipmentEntity(
        name = "テスト",
        massPerMeter = 0.844,
        spanMeters = 1.0,
        standardTension = 160.0,
        specLower = 150.0,
        specUpper = 180.0,
        widthMm = 86.0,
        thicknessMm = 1.25
    )

    @Test
    fun stringModelGivesLowFundamentalAtOneMeterSpan() {
        val freq = TensionCalculator.frequencyHzString(0.844, 1.0, 160.0)
        assertTrue(abs(freq - 6.88) < 0.2)
    }

    @Test
    fun beamModelGivesTensionSensitiveFrequencyForDefaultEquipment() {
        val freq = TensionCalculator.frequencyFromEquipment(defaultEquipment, 160.0)
        assertTrue("expected ~5-15 Hz fundamental but was $freq", freq in 5.0..15.0)
    }

    @Test
    fun beamModelRoundTripsTension() {
        val tensions = listOf(150.0, 160.0, 180.0)
        for (target in tensions) {
            val freq = TensionCalculator.frequencyFromEquipment(defaultEquipment, target)
            val roundTrip = TensionCalculator.tensionFromEquipment(defaultEquipment, freq)
            assertEquals(target, roundTrip, 1.0)
        }
    }

    @Test
    fun fallsBackToStringModelWithoutBladeDimensions() {
        val bare = defaultEquipment.copy(widthMm = 0.0, thicknessMm = 0.0)
        val beam = TensionCalculator.tensionFromEquipment(bare, 85.0)
        val stringOnly = TensionCalculator.tensionNewtonsString(0.844, 1.0, 85.0)
        assertEquals(stringOnly, beam, 0.01)
    }

    @Test
    fun edgewiseBendingIsHigherThanFlatwise() {
        val edge = TensionCalculator.frequencyFromEquipment(defaultEquipment, 160.0)
        val flat = TensionCalculator.frequencyFromEquipment(
            defaultEquipment.copy(edgewiseBending = false),
            160.0
        )
        assertTrue(edge > flat)
    }

    @Test
    fun flexuralRigidityUsesWidthAndThickness() {
        val rigidity = defaultEquipment.beamFlexuralRigidity()
        assertTrue(rigidity != null && rigidity > 0)
    }
}
