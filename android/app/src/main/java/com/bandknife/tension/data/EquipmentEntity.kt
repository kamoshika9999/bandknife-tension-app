package com.bandknife.tension.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val massPerMeter: Double,
    val spanMeters: Double,
    val standardTension: Double,
    val specLower: Double,
    val specUpper: Double,
    val useHzMode: Boolean = false,
    val standardHz: Double = 0.0,
    val specHzLower: Double = 0.0,
    val specHzUpper: Double = 0.0,
    val widthMm: Double = 0.0,
    val thicknessMm: Double = 0.0,
    val density: Double = 7850.0,
    val deleted: Boolean = false
)
