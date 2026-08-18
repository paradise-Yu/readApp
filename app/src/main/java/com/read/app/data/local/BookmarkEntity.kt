package com.read.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    // 书签所在段落索引（阅读器分段后的下标）
    val paragraphIndex: Int,
    // 段落内容预览，便于在书签列表中识别
    val snippet: String,
    val createdTime: Long = System.currentTimeMillis()
)
