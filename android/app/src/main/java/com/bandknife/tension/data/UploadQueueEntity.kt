package com.bandknife.tension.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
