package com.read.app.domain.repository

import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    fun searchBooksByTag(tagName: String): Flow<List<Book>>
    fun getBooksByFolder(folderId: Long): Flow<List<Book>>
    suspend fun getBookById(id: Long): Book?
    suspend fun getBookByPath(filePath: String): Book?
    suspend fun insertBook(book: Book): Long
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun updateRating(bookId: Long, rating: Int)
    suspend fun updateReadProgress(bookId: Long, time: Long, progress: String?)
}

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun getAllTagsOnce(): List<Tag>
    suspend fun insertTag(tag: Tag): Long
    suspend fun deleteTag(tag: Tag)
    fun getTagsForBook(bookId: Long): Flow<List<Tag>>
    suspend fun addTagToBook(bookId: Long, tagId: Long)
    suspend fun removeTagFromBook(bookId: Long, tagId: Long)
}

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun insertFolder(folder: Folder): Long
    suspend fun deleteFolder(folder: Folder)
    suspend fun getFolderById(id: Long): Folder?
    suspend fun setFolderPassword(id: Long, password: String?)
}
