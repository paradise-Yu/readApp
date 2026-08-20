package com.read.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.repository.TagRepository
import com.read.app.domain.session.ReaderSession
import com.read.app.util.BookImporter
import com.read.app.util.FolderContentCache
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
    private val tagRepository: TagRepository,
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

    // 标签筛选（null = 全部）
    private val _selectedTagName = MutableStateFlow<String?>(null)
    val selectedTagName: StateFlow<String?> = _selectedTagName.asStateFlow()

    // 全部公共标签
    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 是否处于隐身模式
    val inSecretMode: StateFlow<Boolean> = session.secretPassword
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 密码和书籍缓存从共享缓存读取（与 FolderViewModel 保持一致）
    // 切换文件夹时直接读缓存，无需重新过滤全部书籍

    init {
        // 初始化缓存：读取密码 + 构建文件夹书籍映射
        viewModelScope.launch {
            val folders = folderRepository.getAllFolders().first()
            val allBooks = bookRepository.getAllBooks().first()
            FolderContentCache.init(context, folders, allBooks)
        }
        // 监听书籍变化，重建缓存
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { allBooks ->
                val folders = folderRepository.getAllFolders().first()
                FolderContentCache.rebuild(context, folders, allBooks)
            }
        }
        // 文件夹变化时也重建缓存（新增/删除文件夹）
        viewModelScope.launch {
            folderRepository.getAllFolders().collect { folders ->
                val allBooks = bookRepository.getAllBooks().first()
                FolderContentCache.rebuild(context, folders, allBooks)
            }
        }
    }

    // 当前模式下可见的文件夹：普通模式只显示未设密码的；隐身模式只显示绑定当前密码的
    val visibleFolders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword,
        FolderContentCache.pwdMap
    ) { folders, pwd, pwdMap ->
        if (pwd == null) folders.filter { pwdMap[it.id] == null }
        else folders.filter { pwdMap[it.id] == pwd }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 书架书籍：从共享缓存中读取，切换文件夹时秒开
    val books: StateFlow<List<Book>> = combine(
        FolderContentCache.pwdMap,
        _selectedFolderId,
        _sortOrder,
        _selectedTagName
    ) { _, folderId, order, tagName ->
        val books = FolderContentCache.getBooksForFolder(folderId)
        val filtered = if (tagName == null) books else books.filter { b -> b.tags.any { it.name == tagName } }
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

    // 再次点击已选中的标签则取消筛选
    fun setTagFilter(tagName: String?) {
        _selectedTagName.value = if (_selectedTagName.value == tagName) null else tagName
    }

    // 输入密码进入隐身模式：从缓存比对，无需读文件
    fun enterSecretMode(password: String) {
        viewModelScope.launch {
            val matched = FolderContentCache.pwdMap.value.values.any { it == password }
            if (matched) {
                session.enterSecretMode(password)
                _selectedFolderId.value = null
                _selectedTagName.value = null
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
        _selectedTagName.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                FolderContentCache.clear()
                val allFolders = folderRepository.getAllFolders().first()
                allFolders.forEach { folder ->
                    scanFolderInternal(folder.path, folder.id)
                }
                // 重建缓存
                val folders = folderRepository.getAllFolders().first()
                val allBooks = bookRepository.getAllBooks().first()
                FolderContentCache.rebuild(context, folders, allBooks)
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
