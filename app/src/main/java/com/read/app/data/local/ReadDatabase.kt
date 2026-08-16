package com.read.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, TagEntity::class, BookTagEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
}
