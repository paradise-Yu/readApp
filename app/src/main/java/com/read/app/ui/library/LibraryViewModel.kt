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
import com.read.app.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // 密码以明文文件形式存放在各文件夹内，文件为唯一可信来源（手工删除文件即解除隐藏）
    private val _pwdMap = MutableStateFlow<Map<Long, String?>>(emptyMap())

    init {
        viewModelScope.launch {
            folderRepository.getAllFolders().collect { folders ->
                _pwdMap.value = withContext(Dispatchers.IO) {
                    folders.associate { it.id to FileUtil.readFolderPassword(context, it.path) }
                }
            }
        }
    }

    // 当前模式下可见的文件夹：普通模式只显示未设密码的；隐身模式只显示绑定当前密码的
    val visibleFolders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword,
        _pwdMap
    ) { folders, pwd, pwdMap ->
        if (pwd == null) folders.filter { pwdMap[it.id] == null }
        else folders.filter { pwdMap[it.id] == pwd }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 筛选状态：文件夹 / 标签 / 隐身密码（combine 最多 5 路，先合并）
    private val filterState = combine(
        _selectedFolderId,
        _selectedTagName,
        session.secretPassword
    ) { folderId, tagName, pwd -> Triple(folderId, tagName, pwd) }

    // 书架缓存：避免每次 combine 都重新查询和排序
    private val _booksCache = MutableStateFlow<List<Book>>(emptyList())

    val books: StateFlow<List<Book>> = combine(
        bookRepository.getAllBooks(),
        _sortOrder,
        filterState,
        folderRepository.getAllFolders(),
        _pwdMap
    ) { all, order, filter, folders, pwdMap ->
        val (folderId, tagName, pwd) = filter
        val allFolderIds = folders.map { it.id }.toSet()
        val hiddenFolderIds = folders.filter { pwdMap[it.id] != null }.map { it.id }.toSet()
        val filtered = if (pwd != null) {
            // 隐身模式：只显示绑定该密码的文件夹下的书
            val ids = folders.filter { pwdMap[it.id] == pwd }.map { it.id }.toSet()
            all.filter { it.folderId in ids }
        } else {
            // 普通模式：隐藏已设密码文件夹的书 + 孤立书（folderId 不存在），并按选中文件夹筛选
            all.filter { b ->
                val isOrphaned = b.folderId != null && b.folderId !in allFolderIds
                (b.folderId == null || (b.folderId !in hiddenFolderIds && !isOrphaned)) &&
                    (folderId == null || b.folderId == folderId)
            }
        }.let { list ->
            // 标签筛选
            if (tagName == null) list else list.filter { b -> b.tags.any { it.name == tagName } }
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

    // 再次点击已选中的标签则取消筛选
    fun setTagFilter(tagName: String?) {
        _selectedTagName.value = if (_selectedTagName.value == tagName) null else tagName
    }

    // 输入密码进入隐身模式；与文件夹内密码文件内容比对，无匹配时提示
    fun enterSecretMode(password: String) {
        viewModelScope.launch {
            val folders = folderRepository.getAllFolders().first()
            val matched = withContext(Dispatchers.IO) {
                folders.any { FileUtil.readFolderPassword(context, it.path) == password }
            }
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
                val allFolders = folderRepository.getAllFolders().first()
                allFolders.forEach { folder ->
                    scanFolderInternal(folder.path, folder.id)
                }
                // 重读密码文件：用户可能手工删除/修改了文件夹内的密码文件
                _pwdMap.value = withContext(Dispatchers.IO) {
                    allFolders.associate { it.id to FileUtil.readFolderPassword(context, it.path) }
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
