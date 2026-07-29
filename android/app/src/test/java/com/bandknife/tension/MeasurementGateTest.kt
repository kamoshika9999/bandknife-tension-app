package com.bandknife.tension

import com.bandknife.tension.domain.GateStatus
import com.bandknife.tension.domain.MeasurementGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MeasurementGateTest {
    private val now = 1_700_000_000_000L

    private fun gateAfterDays(days: Double) = MeasurementGate(
        lastSyncAt = now - (TimeUnit.DAYS.toMillis(1) * days).toLong(),
        now = now
    )

    @Test
    fun `一度も確認できていなければ測定できない`() {
        val gate = MeasurementGate(lastSyncAt = 0L, now = now)
        assertTrue(gate.neverSynced)
        assertEquals(GateStatus.BLOCKED, gate.status)
        assertFalse(gate.canMeasure)
    }

    @Test
    fun `確認直後は有効で警告も出ない`() {
        val gate = gateAfterDays(0.0)
        assertEquals(GateStatus.VALID, gate.status)
        assertTrue(gate.canMeasure)
        assertEquals(7, gate.remainingDays)
    }

    @Test
    fun `残り2日を切ると警告するが測定はできる`() {
        val gate = gateAfterDays(5.5)
        assertEquals(GateStatus.EXPIRING, gate.status)
        assertTrue(gate.canMeasure)
        assertEquals(2, gate.remainingDays)
    }

    @Test
    fun `7日を1分でも超えたら測定できない`() {
        val gate = MeasurementGate(
            lastSyncAt = now - TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(1),
            now = now
        )
        assertEquals(GateStatus.BLOCKED, gate.status)
        assertFalse(gate.canMeasure)
        assertEquals(0, gate.remainingDays)
    }

    @Test
    fun `7日ちょうどは期限切れとして扱う`() {
        val gate = gateAfterDays(7.0)
        assertEquals(GateStatus.BLOCKED, gate.status)
    }

    @Test
    fun `経過日数は切り捨てで数える`() {
        assertEquals(9, gateAfterDays(9.8).elapsedDays)
    }

    @Test
    fun `端末の時計が巻き戻っても期限切れ扱いにしない`() {
        val gate = MeasurementGate(lastSyncAt = now + TimeUnit.DAYS.toMillis(1), now = now)
        assertEquals(GateStatus.VALID, gate.status)
    }
}
