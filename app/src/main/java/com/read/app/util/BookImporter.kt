package com.read.app.util

import android.content.Context
import com.read.app.data.parser.ParserFactory
import com.read.app.domain.model.Book
import com.read.app.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 扫描文件夹并导入书籍到数据库（同时支持 SAF content:// 目录和本地路径）
object BookImporter {

    suspend fun scanAndImport(
        context: Context,
        bookRepository: BookRepository,
        folderPath: String,
        folderId: Long
    ): Int = withContext(Dispatchers.IO) {
        if (FileUtil.isUriPath(folderPath)) {
            importFromTree(context, bookRepository, folderPath, folderId)
        } else {
            importFromDirectory(bookRepository, folderPath, folderId)
        }
    }

    // SAF 目录：存储文档 URI 字符串，读取走 ContentResolver（无需存储权限）
    private suspend fun importFromTree(
        context: Context,
        bookRepository: BookRepository,
        treeUri: String,
        folderId: Long
    ): Int {
        var added = 0
        FileUtil.scanTreeForBooks(context, treeUri).forEach { doc ->
            if (bookRepository.getBookByPath(doc.uri) == null) {
                bookRepository.insertBook(
                    Book(
                        title = doc.name.substringBeforeLast('.'),
                        author = null,
                        filePath = doc.uri,
                        format = FileUtil.bookFormatOf(doc.name),
                        fileSize = doc.size,
                        folderId = folderId
                    )
                )
                added++
            }
        }
        return added
    }

    // 本地目录：兼容旧数据，保留解析器提取元数据
    private suspend fun importFromDirectory(
        bookRepository: BookRepository,
        path: String,
        folderId: Long
    ): Int {
        var added = 0
        FileUtil.scanDirectoryForBooks(path).forEach { file ->
            if (bookRepository.getBookByPath(file.absolutePath) == null) {
                try {
                    val format = file.extension.lowercase()
                    val metadata = ParserFactory.getParser(format).extractMetadata(file)
                    bookRepository.insertBook(
                        Book(
                            title = metadata.title,
                            author = metadata.author,
                            filePath = file.absolutePath,
                            format = format,
                            fileSize = file.length(),
                            folderId = folderId
                        )
                    )
                    added++
                } catch (_: Exception) {}
            }
        }
        return added
    }
}
