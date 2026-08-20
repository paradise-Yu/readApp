package com.read.app.domain.model

data class Book(
    val id: Long = 0,
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
    val folderId: Long? = null,
    val tags: List<Tag> = emptyList()
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: Int = 0xFF4CAF50.toInt()
)

data class Folder(
    val id: Long = 0,
    val path: String,
    val name: String,
    val addedTime: Long = System.currentTimeMillis(),
    val password: String? = null
) {
    // 显示名称：兼容旧数据（name 可能是全路径），始终只取最后一级
    val displayName: String
        get() {
            if (name.contains('/')) return name.substringAfterLast('/')
            if (name.contains(':')) return name.substringAfterLast(':')
            return name
        }
}

data class Bookmark(
    val id: Long = 0,
    val bookId: Long,
    val paragraphIndex: Int,
    val snippet: String,
    val createdTime: Long = System.currentTimeMillis()
)
