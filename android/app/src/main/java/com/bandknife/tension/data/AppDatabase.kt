package com.bandknife.tension.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EquipmentEntity::class, MeasurementRecordEntity::class, UploadQueueEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun equipmentDao(): EquipmentDao
    abstract fun recordDao(): RecordDao
    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bandknife_tension.db"
                ).build().also { instance = it }
            }
        }
    }
}
