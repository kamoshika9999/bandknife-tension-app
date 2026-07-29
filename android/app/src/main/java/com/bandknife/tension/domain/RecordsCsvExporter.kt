package com.bandknife.tension.domain

import com.bandknife.tension.data.MeasurementRecordEntity
import java.text.SimpleDateFormat
import java.util.Locale

/** 測定履歴を CSV 形式にする。ローカルモードの記録も区別列付きで含める。 */
object RecordsCsvExporter {
    private val TIME_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)

    fun export(records: List<MeasurementRecordEntity>): String {
        val header = listOf(
            "日時",
            "設備名",
            "周波数(Hz)",
            "張力(N)",
            "合否",
            "n数",
            "標準偏差",
            "CI下限",
            "CI上限",
            "コメント",
            "端末のみ"
        ).joinToString(",")
        val rows = records.sortedByDescending { it.timestamp }.map { record ->
            listOf(
                TIME_FORMAT.format(record.timestamp),
                record.equipmentName,
                record.frequencyHz,
                record.tensionN,
                if (record.passed) "OK" else "要調整",
                record.sampleCount,
                record.stdDev,
                record.ci95Lower,
                record.ci95Upper,
                record.comment,
                if (record.localOnly) "はい" else "いいえ"
            ).joinToString(",") { escapeCsv(it.toString()) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
