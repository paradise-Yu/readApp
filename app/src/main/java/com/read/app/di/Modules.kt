package com.read.app.di

import android.content.Context
import androidx.room.Room
import com.read.app.data.local.BookDao
import com.read.app.data.local.FolderDao
import com.read.app.data.local.ReadDatabase
import com.read.app.data.local.TagDao
import com.read.app.data.repository.BookRepositoryImpl
import com.read.app.data.repository.FolderRepositoryImpl
import com.read.app.data.repository.TagRepositoryImpl
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadDatabase =
        Room.databaseBuilder(context, ReadDatabase::class.java, "read_database")
            .addMigrations(ReadDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideBookDao(database: ReadDatabase): BookDao = database.bookDao()

    @Provides
    fun provideTagDao(database: ReadDatabase): TagDao = database.tagDao()

    @Provides
    fun provideFolderDao(database: ReadDatabase): FolderDao = database.folderDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
