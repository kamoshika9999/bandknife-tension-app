package com.bandknife.tension

import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.domain.RecordsCsvExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsCsvExporterTest {
    @Test
    fun `端末のみの記録はCSVに区別列が付く`() {
        val csv = RecordsCsvExporter.export(
            listOf(
                MeasurementRecordEntity(
                    id = 1,
                    equipmentId = 1,
                    equipmentName = "テスト設備",
                    tensionN = 160.0,
                    frequencyHz = 85.0,
                    passed = true,
                    localOnly = true
                )
            )
        )
        assertTrue(csv.contains("端末のみ"))
        assertTrue(csv.contains("はい"))
    }
}
