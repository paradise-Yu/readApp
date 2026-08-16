package com.read.app.data.repository

import com.read.app.data.local.BookDao
import com.read.app.data.local.BookEntity
import com.read.app.data.local.TagDao
import com.read.app.data.local.TagEntity
import com.read.app.domain.model.Book
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val tagDao: TagDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { entities ->
            entities.toBooksWithTags()
        }

    override fun searchBooks(query: String): Flow<List<Book>> =
        bookDao.searchBooks(query).map { entities ->
            entities.toBooksWithTags()
        }

    override fun searchBooksByTag(tagName: String): Flow<List<Book>> =
        bookDao.searchBooksByTag(tagName).map { entities ->
            entities.toBooksWithTags()
        }

    override fun getBooksByFolder(folderId: Long): Flow<List<Book>> =
        bookDao.getBooksByFolder(folderId).map { entities ->
            entities.toBooksWithTags()
        }

    override suspend fun getBookById(id: Long): Book? =
        bookDao.getBookById(id)?.toBookWithTags()

    override suspend fun getBookByPath(filePath: String): Book? =
        bookDao.getBookByPath(filePath)?.toBookWithTags()

    override suspend fun insertBook(book: Book): Long =
        bookDao.insertBook(book.toEntity())

    override suspend fun updateBook(book: Book) =
        bookDao.updateBook(book.toEntity())

    override suspend fun deleteBook(book: Book) =
        bookDao.deleteBook(book.toEntity())

    override suspend fun updateRating(bookId: Long, rating: Int) =
        bookDao.updateRating(bookId, rating)

    override suspend fun updateReadProgress(bookId: Long, time: Long, progress: String?) =
        bookDao.updateReadProgress(bookId, time, progress)

    private suspend fun BookEntity.toBookWithTags(): Book {
        val tagEntities = tagDao.getTagsForBookOnce(id)
        return toBook().copy(
            tags = tagEntities.map { it.toTag() }
        )
    }

    private suspend fun List<BookEntity>.toBooksWithTags(): List<Book> = coroutineScope {
        map { entity ->
            async { entity.toBookWithTags() }
        }.map { it.await() }
    }

    private fun TagEntity.toTag() = Tag(id = id, name = name, color = color)

    private fun BookEntity.toBook() = Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        format = format,
        coverPath = coverPath,
        fileSize = fileSize,
        lastReadTime = lastReadTime,
        readProgress = readProgress,
        rating = rating,
        addedTime = addedTime,
        folderId = folderId
    )

    private fun Book.toEntity() = BookEntity(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        format = format,
        coverPath = coverPath,
        fileSize = fileSize,
        lastReadTime = lastReadTime,
        readProgress = readProgress,
        rating = rating,
        addedTime = addedTime,
        folderId = folderId
    )
}
