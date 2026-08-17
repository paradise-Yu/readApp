package com.read.app.data.parser

import java.io.File
import java.nio.charset.Charset

data class BookMetadata(
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val description: String? = null
)

interface BookParser {
    fun extractMetadata(file: File): BookMetadata
    fun readContent(file: File): String
}

class TxtParser : BookParser {
    override fun extractMetadata(file: File): BookMetadata {
        val nameWithoutExt = file.nameWithoutExtension
        return BookMetadata(title = nameWithoutExt)
    }

    override fun readContent(file: File): String {
        return file.readText(charset = detectCharset(file))
    }

    private fun detectCharset(file: File): Charset {
        val bom = ByteArray(4)
        file.inputStream().use { it.read(bom) }
        return when {
            bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> Charsets.UTF_8
            bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> Charset.forName("UTF-16BE")
            bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }
    }
}

class PdfParser : BookParser {
    override fun extractMetadata(file: File): BookMetadata {
        return BookMetadata(title = file.nameWithoutExtension)
    }

    override fun readContent(file: File): String {
        return "" // PDF content is rendered by AndroidPdfViewer
    }
}

class EpubParser : BookParser {
    override fun extractMetadata(file: File): BookMetadata {
        var title = file.nameWithoutExtension
        var author: String? = null

        try {
            val zipFile = java.util.zip.ZipFile(file)
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            if (containerEntry != null) {
                val containerXml = zipFile.getInputStream(containerEntry).bufferedReader().readText()
                val rootfilePath = Regex("full-path=\"([^\"]+)\"").find(containerXml)?.groupValues?.get(1)
                if (rootfilePath != null) {
                    val opfEntry = zipFile.getEntry(rootfilePath)
                    if (opfEntry != null) {
                        val opfXml = zipFile.getInputStream(opfEntry).bufferedReader().readText()
                        title = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opfXml)?.groupValues?.get(1) ?: title
                        author = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opfXml)?.groupValues?.get(1)
                    }
                }
            }
            zipFile.close()
        } catch (_: Exception) {}

        return BookMetadata(title = title, author = author)
    }

    override fun readContent(file: File): String {
        return "" // EPUB content is rendered via WebView
    }

    fun getHtmlContent(file: File): String {
        val htmlParts = mutableListOf<String>()
        try {
            val zipFile = java.util.zip.ZipFile(file)
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            if (containerEntry != null) {
                val containerXml = zipFile.getInputStream(containerEntry).bufferedReader().readText()
                val rootfilePath = Regex("full-path=\"([^\"]+)\"").find(containerXml)?.groupValues?.get(1)
                if (rootfilePath != null) {
                    val baseDir = rootfilePath.substringBeforeLast("/", "")
                    val opfEntry = zipFile.getEntry(rootfilePath)
                    if (opfEntry != null) {
                        val opfXml = zipFile.getInputStream(opfEntry).bufferedReader().readText()
                        val itemRefs = Regex("idref=\"([^\"]+)\"").findAll(opfXml).map { it.groupValues[1] }.toList()
                        val manifestItems = mutableMapOf<String, String>()
                        Regex("<item[^>]*id=\"([^\"]+)\"[^>]*href=\"([^\"]+)\"[^>]*media-type=\"application/xhtml\\+xml\"").findAll(opfXml).forEach {
                            manifestItems[it.groupValues[1]] = it.groupValues[2]
                        }
                        for (idref in itemRefs) {
                            val href = manifestItems[idref] ?: continue
                            val entryPath = if (baseDir.isNotEmpty()) "$baseDir/$href" else href
                            val entry = zipFile.getEntry(entryPath)
                            if (entry != null) {
                                htmlParts.add(zipFile.getInputStream(entry).bufferedReader().readText())
                            }
                        }
                    }
                }
            }
            zipFile.close()
        } catch (_: Exception) {}
        return htmlParts.joinToString("\n")
    }
}

object ParserFactory {
    fun getParser(format: String): BookParser = when (format.lowercase()) {
        "txt" -> TxtParser()
        "pdf" -> PdfParser()
        "epub" -> EpubParser()
        else -> throw IllegalArgumentException("Unsupported format: $format")
    }
}
