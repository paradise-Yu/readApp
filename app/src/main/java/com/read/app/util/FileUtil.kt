package com.read.app.util

import java.io.File

object FileUtil {
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

    fun scanDirectoryForBooks(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { isSupportedFile(it) }
            .toList()
    }
}
