package com.read.app.util

import android.content.Context
import android.net.Uri
import com.read.app.data.parser.ParserFactory
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

// 外部应用打开文本文件时的处理器：按来源应用创建文件夹，复制文件并导入书库
object ExternalFileHandler {

    // 处理外部打开的文件：uri 为来源应用提供的 content:// URI，sourcePackage 为来源应用包名
    suspend fun handleExternalFile(
        context: Context,
        uri: Uri,
        sourcePackage: String,
        bookRepository: BookRepository,
        folderRepository: FolderRepository
    ): Long? = withContext(Dispatchers.IO) {
        try {
            // 解析来源应用名（取包名最后一段，如 com.tencent.mobileqq → QQ）
            val sourceName = resolveSourceName(sourcePackage)

            // 在应用外部存储下创建来源文件夹：/sdcard/Android/data/com.read.app/files/external_imports/QQ/
            val baseDir = context.getExternalFilesDir("external_imports") ?: return@withContext null
            val sourceDir = File(baseDir, sourceName)
            if (!sourceDir.exists()) sourceDir.mkdirs()

            // 从 URI 读取文件名
            val fileName = extractFileName(context, uri) ?: return@withContext null
            val targetFile = File(sourceDir, fileName)

            // 复制文件到目标文件夹
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // 查找或创建对应的文件夹记录
            val folderPath = sourceDir.absolutePath
            val folderId = folderRepository.getAllFolders().first().firstOrNull { it.path == folderPath }?.id
                ?: folderRepository.insertFolder(Folder(path = folderPath, name = sourceName))

            // 检查是否已导入过（按文件路径去重）
            val existingBook = bookRepository.getBookByPath(targetFile.absolutePath)
            if (existingBook != null) return@withContext existingBook.id

            // 解析元数据并导入书库
            val format = targetFile.extension.lowercase()
            val metadata = try {
                ParserFactory.getParser(format).extractMetadata(targetFile)
            } catch (_: Exception) {
                null
            }

            val bookId = bookRepository.insertBook(
                Book(
                    title = metadata?.title ?: fileName.substringBeforeLast('.'),
                    author = metadata?.author,
                    filePath = targetFile.absolutePath,
                    format = format,
                    fileSize = targetFile.length(),
                    folderId = folderId
                )
            )
            bookId
        } catch (_: Exception) {
            null
        }
    }

    // 解析来源应用包名为可读名称：com.tencent.mobileqq → QQ，com.example.myapp → Myapp
    private fun resolveSourceName(packageName: String): String {
        val shortName = packageName.substringAfterLast('.')
        // 常见应用映射（可扩展）
        return when (packageName) {
            "com.tencent.mobileqq" -> "QQ"
            "com.tencent.mm" -> "WeChat"
            "com.eg.android.AlipayGphone" -> "Alipay"
            "com.tencent.wework" -> "WeCom"
            else -> shortName.replaceFirstChar { it.uppercaseChar() }
        }
    }

    // 从 content:// URI 提取文件名
    private fun extractFileName(context: Context, uri: Uri): String? {
        // 优先从 URI 的 lastPathSegment 提取
        val segment = uri.lastPathSegment
        if (segment != null && segment.contains('.')) {
            return segment.substringAfterLast('/').let { 
                // 处理 DocumentFile 风格的 "primary:path/filename.txt"
                if (it.contains(':')) it.substringAfterLast(':') else it
            }
        }
        // 回退：查询 ContentResolver 的 DISPLAY_NAME
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
