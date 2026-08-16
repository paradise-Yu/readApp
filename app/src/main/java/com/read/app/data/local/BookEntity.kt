package com.read.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    val format: String,
    val coverPath: String? = null,
    val fileSize: Long = 0L,
    val lastReadTime: Long? = null,
    val readProgress: String? = null,
    val rating: Int? = null,
    val addedTime: Long = System.currentTimeMillis(),
    val folderId: Long? = null
)
