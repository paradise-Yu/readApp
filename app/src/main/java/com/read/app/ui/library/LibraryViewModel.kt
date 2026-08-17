package com.read.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.util.BookImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder(val label: String) {
    RECENT("最近阅读"),
    TITLE("书名"),
    ADDED("添加时间")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val folderRepository: FolderRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val books: StateFlow<List<Book>> = combine(
        bookRepository.getAllBooks(),
        _sortOrder
    ) { books, order ->
        when (order) {
            SortOrder.RECENT -> books.sortedByDescending { it.lastReadTime ?: 0L }
            SortOrder.TITLE -> books.sortedBy { it.title }
            SortOrder.ADDED -> books.sortedByDescending { it.addedTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val allFolders = folderRepository.getAllFolders().first()
                allFolders.forEach { folder ->
                    scanFolderInternal(folder.path, folder.id)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun scanFolder(uri: Uri, path: String, name: String) {
        viewModelScope.launch {
            // Persist URI permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val folderId = folderRepository.insertFolder(Folder(path = path, name = name))
            scanFolderInternal(path, folderId)
        }
    }

    private suspend fun scanFolderInternal(path: String, folderId: Long) {
        val addedCount = BookImporter.scanAndImport(context, bookRepository, path, folderId)
        if (addedCount > 0) {
            _scanMessage.value = "添加了 $addedCount 本书"
        }
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
