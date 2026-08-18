package com.read.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, TagEntity::class, BookTagEntity::class, FolderEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        // v1 -> v2：folders 表新增访问密码字段
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN password TEXT DEFAULT NULL")
            }
        }

        // v2 -> v3：新增书签表
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bookmarks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "bookId INTEGER NOT NULL, " +
                        "paragraphIndex INTEGER NOT NULL, " +
                        "snippet TEXT NOT NULL, " +
                        "createdTime INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks (bookId)")
            }
        }
    }
}
