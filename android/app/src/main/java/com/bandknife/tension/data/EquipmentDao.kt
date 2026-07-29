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

    @Query("SELECT * FROM equipment WHERE deleted = 1 ORDER BY deletedAt DESC, name")
    fun observeDeleted(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: Long): EquipmentEntity?

    @Query("SELECT * FROM equipment WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): EquipmentEntity?

    /** ドライブ側に無くなった設備を測定不可に落とす。履歴が参照するため行自体は残す。 */
    @Query("UPDATE equipment SET syncedAt = NULL WHERE uuid NOT IN (:uuids)")
    suspend fun markUnsyncedExcept(uuids: List<String>)

    @Query("UPDATE equipment SET syncedAt = NULL")
    suspend fun markAllUnsynced()

    @Query("SELECT COUNT(*) FROM equipment WHERE deleted = 0")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EquipmentEntity): Long

    @Update
    suspend fun update(entity: EquipmentEntity)

    @Query("UPDATE equipment SET deleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE equipment SET deleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM equipment WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)
}
