package com.read.app.data.repository

import com.read.app.data.local.FolderDao
import com.read.app.data.local.FolderEntity
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> =
        folderDao.getAllFolders().map { entities -> entities.map { it.toFolder() } }

    override suspend fun insertFolder(folder: Folder): Long =
        folderDao.insertFolder(folder.toEntity())

    override suspend fun deleteFolder(folder: Folder) =
        folderDao.deleteFolder(folder.toEntity())

    override suspend fun getFolderById(id: Long): Folder? =
        folderDao.getFolderById(id)?.toFolder()

    private fun FolderEntity.toFolder() = Folder(id = id, path = path, name = name, addedTime = addedTime)
    private fun Folder.toEntity() = FolderEntity(id = id, path = path, name = name, addedTime = addedTime)
}
