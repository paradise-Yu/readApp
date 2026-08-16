package com.read.app.data.repository

import com.read.app.data.local.BookTagEntity
import com.read.app.data.local.TagDao
import com.read.app.data.local.TagEntity
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> =
        tagDao.getAllTags().map { entities -> entities.map { it.toTag() } }

    override suspend fun getAllTagsOnce(): List<Tag> =
        tagDao.getAllTagsOnce().map { it.toTag() }

    override suspend fun insertTag(tag: Tag): Long =
        tagDao.insertTag(tag.toEntity())

    override suspend fun deleteTag(tag: Tag) =
        tagDao.deleteTag(tag.toEntity())

    override fun getTagsForBook(bookId: Long): Flow<List<Tag>> =
        tagDao.getTagsForBook(bookId).map { entities -> entities.map { it.toTag() } }

    override suspend fun addTagToBook(bookId: Long, tagId: Long) =
        tagDao.insertBookTag(BookTagEntity(bookId = bookId, tagId = tagId))

    override suspend fun removeTagFromBook(bookId: Long, tagId: Long) =
        tagDao.deleteBookTag(bookId, tagId)

    private fun TagEntity.toTag() = Tag(id = id, name = name, color = color)
    private fun Tag.toEntity() = TagEntity(id = id, name = name, color = color)
}
