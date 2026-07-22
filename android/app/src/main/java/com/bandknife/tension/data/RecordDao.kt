package com.bandknife.tension.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM measurement_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MeasurementRecordEntity>>

    @Query("SELECT * FROM measurement_records WHERE equipmentId = :equipmentId ORDER BY timestamp DESC")
    fun observeByEquipment(equipmentId: Long): Flow<List<MeasurementRecordEntity>>

    @Query("SELECT * FROM measurement_records WHERE id = :id")
    suspend fun getById(id: Long): MeasurementRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MeasurementRecordEntity): Long

    @Update
    suspend fun update(entity: MeasurementRecordEntity)

    @Delete
    suspend fun delete(entity: MeasurementRecordEntity)

    @Query("DELETE FROM measurement_records WHERE timestamp BETWEEN :from AND :to AND isLocked = 0")
    suspend fun deleteInRange(from: Long, to: Long)

    @Query("SELECT * FROM measurement_records WHERE uploaded = 0")
    suspend fun getNotUploaded(): List<MeasurementRecordEntity>

    @Query("UPDATE measurement_records SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)
}
