package com.read.app.data.repository

import com.read.app.data.local.BookmarkDao
import com.read.app.data.local.BookmarkEntity
import com.read.app.domain.model.Bookmark
import com.read.app.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getBookmarks(bookId: Long): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarks(bookId).map { list -> list.map { it.toBookmark() } }

    override suspend fun addBookmark(bookmark: Bookmark): Long =
        bookmarkDao.insert(bookmark.toEntity())

    override suspend fun deleteBookmark(bookmark: Bookmark) =
        bookmarkDao.delete(bookmark.toEntity())

    private fun BookmarkEntity.toBookmark() =
        Bookmark(id = id, bookId = bookId, paragraphIndex = paragraphIndex, snippet = snippet, createdTime = createdTime)

    private fun Bookmark.toEntity() =
        BookmarkEntity(id = id, bookId = bookId, paragraphIndex = paragraphIndex, snippet = snippet, createdTime = createdTime)
}
