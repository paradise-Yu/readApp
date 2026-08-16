package com.read.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("SELECT t.* FROM tags t INNER JOIN book_tags bt ON t.id = bt.tagId WHERE bt.bookId = :bookId")
    fun getTagsForBook(bookId: Long): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t INNER JOIN book_tags bt ON t.id = bt.tagId WHERE bt.bookId = :bookId")
    suspend fun getTagsForBookOnce(bookId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTag(bookTag: BookTagEntity)

    @Query("DELETE FROM book_tags WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun deleteBookTag(bookId: Long, tagId: Long)
}
