package com.bandknife.tension

import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.domain.GasRecordsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GasRecordsFormatTest {
    @Test
    fun toCsvRow_matchesGasFormat() {
        val record = MeasurementRecordEntity(
            equipmentId = 0L,
            timestamp = 1780272600000,
            equipmentName = "ペフ用スライサー1号",
            frequencyHz = 85.0,
            tensionN = 160.0,
            passed = true,
            sampleCount = 3,
            stdDev = 1.2,
            ci95Lower = 158.0,
            ci95Upper = 162.0,
            comment = "テスト\"コメント"
        )
        val row = GasRecordsFormat.toCsvRow(record)
        assertTrue(row.startsWith("1780272600000,ペフ用スライサー1号,85.0,160.0,true,3,1.2,158.0,162.0,"))
        assertTrue(row.contains("\"テスト\"\"コメント\""))
    }

    @Test
    fun mergeBodies_deduplicatesByTimestamp() {
        val a = "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n" +
            "100,設備A,80.0,150.0,true,1,0.0,150.0,150.0,\"\"\n"
        val b = "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n" +
            "100,設備A更新,81.0,151.0,false,2,0.1,150.0,152.0,\"\"\n" +
            "200,設備B,82.0,152.0,true,1,0.0,152.0,152.0,\"\"\n"
        val merged = GasRecordsFormat.mergeBodies(a, b)
        assertTrue(merged.contains("100,設備A更新"))
        assertFalse(merged.contains("100,設備A,80.0"))
        assertTrue(merged.contains("200,設備B"))
    }

    @Test
    fun containsTimestamp_detectsExistingRow() {
        val body = "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n" +
            "1780272600000,設備,80.0,150.0,true,1,0.0,150.0,150.0,\"\"\n"
        assertTrue(GasRecordsFormat.containsTimestamp(body, 1780272600000))
        assertFalse(GasRecordsFormat.containsTimestamp(body, 999))
    }
}
