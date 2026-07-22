package com.bandknife.tension.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY name")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment ORDER BY name")
    suspend fun getAll(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: Long): EquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EquipmentEntity): Long

    @Update
    suspend fun update(entity: EquipmentEntity)

    @Delete
    suspend fun delete(entity: EquipmentEntity)
}
