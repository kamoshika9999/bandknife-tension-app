package com.bandknife.tension.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EquipmentEntity::class, MeasurementRecordEntity::class, UploadQueueEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun equipmentDao(): EquipmentDao
    abstract fun recordDao(): RecordDao
    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment ADD COLUMN deletedAt INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE equipment ADD COLUMN syncedAt INTEGER")
                // 既存設備にも識別子を配り、ドライブへ登録したときに重複せず紐付くようにする。
                // syncedAt は NULL のままにして、ドライブ登録前は測定に使えない状態にする。
                db.execSQL("UPDATE equipment SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE measurement_records ADD COLUMN localOnly INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE equipment ADD COLUMN youngModulusGpa REAL NOT NULL DEFAULT 210.0"
                )
                db.execSQL(
                    "ALTER TABLE equipment ADD COLUMN materialId TEXT NOT NULL DEFAULT 'carbon_steel'"
                )
                db.execSQL(
                    "ALTER TABLE equipment ADD COLUMN vibrationMode INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE equipment ADD COLUMN edgewiseBending INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE measurement_records ADD COLUMN noiseWaveformPath TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE measurement_records ADD COLUMN tapWaveformsJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bandknife_tension.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7
                )
                    .build().also { instance = it }
            }
        }
    }
}
