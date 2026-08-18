package com.read.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.session.ReaderSession
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
    private val session: ReaderSession,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 文件夹筛选（null = 全部）
    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    // 是否处于隐身模式
    val inSecretMode: StateFlow<Boolean> = session.secretPassword
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 当前模式下可见的文件夹：普通模式只显示未设密码的；隐身模式只显示绑定当前密码的
    val visibleFolders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword
    ) { folders, pwd ->
        if (pwd == null) folders.filter { it.password == null }
        else folders.filter { it.password == pwd }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<Book>> = combine(
        bookRepository.getAllBooks(),
        _sortOrder,
        _selectedFolderId,
        session.secretPassword,
        folderRepository.getAllFolders()
    ) { all, order, folderId, pwd, folders ->
        val hiddenFolderIds = folders.filter { it.password != null }.map { it.id }.toSet()
        val filtered = if (pwd != null) {
            // 隐身模式：只显示绑定该密码的文件夹下的书
            val ids = folders.filter { it.password == pwd }.map { it.id }.toSet()
            all.filter { it.folderId in ids }
        } else {
            // 普通模式：隐藏已设密码文件夹的书，并按选中文件夹筛选
            all.filter { b ->
                (b.folderId == null || b.folderId !in hiddenFolderIds) &&
                    (folderId == null || b.folderId == folderId)
            }
        }
        when (order) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.lastReadTime ?: 0L }
            SortOrder.TITLE -> filtered.sortedBy { it.title }
            SortOrder.ADDED -> filtered.sortedByDescending { it.addedTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = visibleFolders

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    // 输入密码进入隐身模式；密码无匹配文件夹时提示
    fun enterSecretMode(password: String) {
        viewModelScope.launch {
            val matched = folderRepository.getAllFolders().first().any { it.password == password }
            if (matched) {
                session.enterSecretMode(password)
                _selectedFolderId.value = null
                _scanMessage.value = "已进入隐身模式"
            } else {
                _scanMessage.value = "密码不正确"
            }
        }
    }

    // 退出隐身模式，回到普通列表
    fun exitSecretMode() {
        session.exitSecretMode()
        _selectedFolderId.value = null
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
