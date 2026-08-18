package com.read.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object FileUtil {

    // 文件夹密码文件名：明文存放在文件夹根目录，手工删除该文件即可解除隐藏
    const val PASSWORD_FILE_NAME = "密码.txt"

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes.toFloat() / (1024 * 1024))
        }
    }

    fun getSupportedExtensions() = setOf("txt", "pdf", "epub")

    fun isSupportedFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in getSupportedExtensions()

    fun isBookName(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in getSupportedExtensions()
    }

    fun bookFormatOf(name: String?): String =
        name?.substringAfterLast('.', "")?.lowercase() ?: ""

    fun isUriPath(path: String) = path.startsWith("content://")

    // ---------- 扫描 ----------

    data class ScannedBook(val uri: String, val name: String, val size: Long)

    // SAF 目录递归扫描（content:// tree URI，无需存储权限）
    fun scanTreeForBooks(context: Context, treeUri: String): List<ScannedBook> {
        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        } catch (_: Exception) { null } ?: return emptyList()
        val out = mutableListOf<ScannedBook>()
        walkTree(root, out)
        return out
    }

    private fun walkTree(dir: DocumentFile, out: MutableList<ScannedBook>) {
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory -> walkTree(doc, out)
                // 密码文件不是书籍，跳过
                doc.isFile && doc.name != PASSWORD_FILE_NAME && isBookName(doc.name) ->
                    out.add(ScannedBook(doc.uri.toString(), doc.name ?: "", doc.length()))
            }
        }
    }

    // 传统本地目录扫描（兼容旧数据）
    fun scanDirectoryForBooks(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.name != PASSWORD_FILE_NAME && isSupportedFile(it) }
            .toList()
    }

    // ---------- 文件夹密码文件（明文存放在文件夹根目录） ----------

    // 读取文件夹内的密码文件内容；文件不存在返回 null
    fun readFolderPassword(context: Context, folderPath: String): String? {
        return try {
            if (isUriPath(folderPath)) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(folderPath)) ?: return null
                val pwdFile = root.findFile(PASSWORD_FILE_NAME) ?: return null
                context.contentResolver.openInputStream(pwdFile.uri)?.use { input ->
                    String(input.readBytes(), Charsets.UTF_8).trim()
                }?.takeIf { it.isNotEmpty() }
            } else {
                val f = File(folderPath, PASSWORD_FILE_NAME)
                if (f.exists()) f.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() } else null
            }
        } catch (_: Exception) { null }
    }

    // 写入/删除密码文件：空密码 = 删除文件（等同手工删除，解除隐藏）
    fun writeFolderPassword(context: Context, folderPath: String, password: String?): Boolean {
        return try {
            if (isUriPath(folderPath)) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(folderPath)) ?: return false
                val existing = root.findFile(PASSWORD_FILE_NAME)
                if (password.isNullOrBlank()) {
                    existing?.delete() ?: true
                } else {
                    existing?.delete()
                    val doc = root.createFile("text/plain", PASSWORD_FILE_NAME) ?: return false
                    context.contentResolver.openOutputStream(doc.uri)?.use {
                        it.write(password.toByteArray(Charsets.UTF_8))
                    } ?: return false
                    true
                }
            } else {
                val f = File(folderPath, PASSWORD_FILE_NAME)
                if (password.isNullOrBlank()) {
                    if (f.exists()) f.delete() else true
                } else {
                    f.writeText(password, Charsets.UTF_8)
                    true
                }
            }
        } catch (_: Exception) { false }
    }

    // ---------- 读取 ----------

    fun openInputStream(context: Context, path: String): InputStream? = try {
        if (isUriPath(path)) {
            context.contentResolver.openInputStream(Uri.parse(path))
        } else {
            val f = File(path)
            if (f.exists()) f.inputStream() else null
        }
    } catch (_: Exception) { null }

    // 读取文本内容，自动编码检测：BOM -> UTF-8 严格校验 -> GBK 回退
    // （同时支持 content:// 和本地路径）
    fun readTextAutoCharset(context: Context, path: String): String {
        val bytes = openInputStream(context, path)?.use { it.readBytes() } ?: return ""
        val (bomCharset, offset) = detectBom(bytes)
        if (bomCharset != null) {
            return String(bytes, offset, bytes.size - offset, bomCharset)
        }
        // 无 BOM：先尝试 UTF-8 严格解码，出现非法字节序列则回退 GBK（中文小说常见编码）
        return if (isValidUtf8(bytes)) {
            String(bytes, Charsets.UTF_8)
        } else {
            try {
                String(bytes, Charset.forName("GBK"))
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    // 返回 (BOM 对应的编码, 跳过字节数)；无 BOM 时编码为 null
    private fun detectBom(b: ByteArray): Pair<Charset?, Int> = when {
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() ->
            Charsets.UTF_8 to 3
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() ->
            Charset.forName("UTF-16BE") to 2
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() ->
            Charsets.UTF_16LE to 2
        else -> null to 0
    }

    // UTF-8 严格校验：存在非法字节序列则判定为非 UTF-8（大概率是 GBK）
    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: Exception) {
        false
    }

    // 需要 java.io.File 的组件（如 EPUB 的 ZipFile）：content:// 先复制到缓存目录
    fun resolveFile(context: Context, path: String): File? {
        if (!isUriPath(path)) {
            val f = File(path)
            return if (f.exists()) f else null
        }
        return try {
            val uri = Uri.parse(path)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "book.epub"
            val target = File(context.cacheDir, "books/${uri.toString().hashCode()}_$name")
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.exists()) target else null
        } catch (_: Exception) { null }
    }
}
