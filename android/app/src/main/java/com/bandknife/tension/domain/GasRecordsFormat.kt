package com.bandknife.tension.domain

import com.bandknife.tension.data.MeasurementRecordEntity

/** drive-upload.gs の appendToCsv と同じ 1 行形式。 */
object GasRecordsFormat {
    private const val HEADER =
        "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n"

    fun header(): String = HEADER

    fun toCsvRow(record: MeasurementRecordEntity): String {
        val escaped = record.comment.replace("\"", "\"\"")
        return buildString {
            append(record.timestamp)
            append(',')
            append(record.equipmentName)
            append(',')
            append(record.frequencyHz)
            append(',')
            append(record.tensionN)
            append(',')
            append(record.passed)
            append(',')
            append(record.sampleCount)
            append(',')
            append(record.stdDev)
            append(',')
            append(record.ci95Lower)
            append(',')
            append(record.ci95Upper)
            append(',')
            append('"').append(escaped).append('"')
            append('\n')
        }
    }

    /** 既存 CSV 本文に含まれるタイムスタンプか。 */
    fun containsTimestamp(csvBody: String, timestamp: Long): Boolean =
        csvBody.contains("$timestamp,")

    /** 複数 CSV をタイムスタンプでマージする（GAS 形式のデータ行のみ）。 */
    fun mergeBodies(vararg bodies: String): String {
        val rows = linkedMapOf<Long, String>()
        for (body in bodies) {
            for (line in body.lineSequence().drop(1)) {
                if (line.isBlank()) continue
                val ts = line.substringBefore(',').toLongOrNull() ?: continue
                rows[ts] = line
            }
        }
        return buildString {
            append(HEADER)
            rows.toSortedMap().values.forEach { append(it).append('\n') }
        }
    }
}
