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
)

data class Bookmark(
    val id: Long = 0,
    val bookId: Long,
    val paragraphIndex: Int,
    val snippet: String,
    val createdTime: Long = System.currentTimeMillis()
)
