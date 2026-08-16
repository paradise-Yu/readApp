package com.read.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.data.parser.ParserFactory
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun scanFolder(uri: Uri, path: String, name: String, resolver: (Uri) -> String?) {
        viewModelScope.launch {
            val folderId = folderRepository.insertFolder(Folder(path = path, name = name))
            val folder = folderRepository.getFolderById(folderId.toInt())
            val actualFolderId = folder?.id ?: folderId

            val files = withContext(Dispatchers.IO) {
                scanDirectory(path)
            }

            var addedCount = 0
            files.forEach { file ->
                val existing = bookRepository.getBookByPath(file.absolutePath)
                if (existing == null) {
                    try {
                        val format = file.extension.lowercase()
                        val parser = ParserFactory.getParser(format)
                        val metadata = withContext(Dispatchers.IO) { parser.extractMetadata(file) }
                        bookRepository.insertBook(
                            Book(
                                title = metadata.title,
                                author = metadata.author,
                                filePath = file.absolutePath,
                                format = format,
                                fileSize = file.length(),
                                folderId = actualFolderId
                            )
                        )
                        addedCount++
                    } catch (_: Exception) {}
                }
            }
            _scanMessage.value = "添加了 $addedCount 本书"
        }
    }

    private fun scanDirectory(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val supportedExts = setOf("txt", "pdf", "epub")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExts }
            .toList()
    }

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }
}
