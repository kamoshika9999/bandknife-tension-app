package com.bandknife.tension.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_records")
data class MeasurementRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentId: Long,
    val equipmentName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val frequencyHz: Double,
    val tensionN: Double,
    val passed: Boolean,
    val sampleCount: Int = 1,
    val stdDev: Double = 0.0,
    val ci95Lower: Double = 0.0,
    val ci95Upper: Double = 0.0,
    val comment: String = "",
    val isLocked: Boolean = false,
    val rawValuesJson: String = "",
    val noiseWaveformPath: String = "",
    val tapWaveformsJson: String = "",
    val uploaded: Boolean = false,
    /** ローカルモード中に保存した記録。ドライブへは送らない。 */
    val localOnly: Boolean = false
)
