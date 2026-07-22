package com.bandknife.tension.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UploadQueueDao {
    @Query("SELECT * FROM upload_queue ORDER BY createdAt")
    suspend fun getAll(): List<UploadQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UploadQueueEntity): Long

    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM upload_queue")
    suspend fun count(): Int
}
