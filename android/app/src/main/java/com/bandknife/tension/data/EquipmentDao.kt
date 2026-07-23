package com.bandknife.tension.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE deleted = 0 ORDER BY name")
    suspend fun getAll(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment WHERE deleted = 1 ORDER BY name")
    fun observeDeleted(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: Long): EquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EquipmentEntity): Long

    @Update
    suspend fun update(entity: EquipmentEntity)

    @Query("UPDATE equipment SET deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE equipment SET deleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)
}
