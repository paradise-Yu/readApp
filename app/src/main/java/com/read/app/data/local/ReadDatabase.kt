package com.read.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, TagEntity::class, BookTagEntity::class, FolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao

    companion object {
        // v1 -> v2：folders 表新增访问密码字段
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN password TEXT DEFAULT NULL")
            }
        }
    }
}
