package com.read.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC, addedTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE folderId = :folderId")
    fun getBooksByFolder(folderId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE filePath = :filePath")
    suspend fun getBookByPath(filePath: String): BookEntity?

    @Query("SELECT DISTINCT b.* FROM books b INNER JOIN book_tags bt ON b.id = bt.bookId INNER JOIN tags t ON bt.tagId = t.id WHERE t.name = :tagName")
    fun searchBooksByTag(tagName: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("UPDATE books SET rating = :rating WHERE id = :bookId")
    suspend fun updateRating(bookId: Long, rating: Int)

    @Query("UPDATE books SET lastReadTime = :time, readProgress = :progress WHERE id = :bookId")
    suspend fun updateReadProgress(bookId: Long, time: Long, progress: String?)
}
