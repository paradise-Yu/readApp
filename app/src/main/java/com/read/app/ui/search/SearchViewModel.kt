package com.read.app.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.repository.TagRepository
import com.read.app.domain.session.ReaderSession
import com.read.app.util.FileUtil
import com.read.app.util.FolderContentCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tagRepository: TagRepository,
    private val folderRepository: FolderRepository,
    private val session: ReaderSession,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // 文件夹筛选（null = 全部文件夹）
    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 可见文件夹：与书架一致，普通模式只显示未加密的，隐身模式只显示当前密码绑定的
    val folders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword,
        FolderContentCache.pwdMap
    ) { all, pwd, pwdMap ->
        if (pwd == null) all.filter { pwdMap[it.id] == FolderContentCache.UNENCRYPTED }
        else all.filter { pwdMap[it.id] == pwd }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _results = MutableStateFlow<List<Book>>(emptyList())
    val results: StateFlow<List<Book>> = _results.asStateFlow()

    private val _fullTextResults = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val fullTextResults: StateFlow<Map<Long, List<String>>> = _fullTextResults.asStateFlow()

    // 搜索进行中（显示进度条）
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // 是否已执行过搜索（用于空结果提示）
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null

    // 只记录输入，不触发搜索：搜索改为手动触发，避免每次按键都扫描全部文件导致卡顿
    fun setQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun setSelectedTag(tagName: String?) {
        _selectedTag.value = tagName
        // 已有搜索结果时，切换筛选条件立即重搜
        if (_hasSearched.value) searchByName()
    }

    fun setSelectedFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
        if (_hasSearched.value) searchByName()
    }

    // 文件名搜索：按书名/作者匹配，由搜索按钮、键盘搜索键、输入框失焦触发
    fun searchByName() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val q = _query.value.trim()
            val tag = _selectedTag.value
            val folderId = _selectedFolderId.value

            if (q.isBlank() && tag == null && folderId == null) {
                _results.value = emptyList()
                _fullTextResults.value = emptyMap()
                _hasSearched.value = false
                return@launch
            }
            _hasSearched.value = true
            _isSearching.value = true
            try {
                _fullTextResults.value = emptyMap()
                val visible = visibleFilter(folderId)
                val books = when {
                    q.isNotBlank() -> bookRepository.searchBooks(q).first()
                    tag != null -> bookRepository.searchBooksByTag(tag).first()
                    else -> bookRepository.getAllBooks().first()
                }
                val filtered = books.filter(visible)
                _results.value = if (tag != null && q.isNotBlank()) {
                    filtered.filter { b -> b.tags.any { t -> t.name == tag } }
                } else filtered
            } finally {
                _isSearching.value = false
            }
        }
    }

    // 内容搜索（TXT 全文）：由"搜内容"按钮手动触发，逐本扫描并渐进更新结果
    fun searchContent() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _hasSearched.value = true
            _isSearching.value = true
            try {
                val tag = _selectedTag.value
                val visible = visibleFilter(_selectedFolderId.value)
                val txtBooks = bookRepository.getAllBooks().first()
                    .filter(visible)
                    .filter { it.format.lowercase() == "txt" }
                    .let { if (tag != null) it.filter { b -> b.tags.any { t -> t.name == tag } } else it }

                val matches = mutableMapOf<Long, List<String>>()
                txtBooks.forEach { book ->
                    val lines = withContext(Dispatchers.IO) {
                        try {
                            FileUtil.readTextAutoCharset(context, book.filePath)
                                .lines().filter { it.contains(q, ignoreCase = true) }.take(3)
                        } catch (_: Exception) { emptyList() }
                    }
                    if (lines.isNotEmpty()) {
                        matches[book.id] = lines
                        // 渐进式更新：每扫完一本立即刷新，避免长时间等待无反馈
                        _fullTextResults.value = matches.toMap()
                        _results.value = txtBooks.filter { it.id in matches }
                    }
                }
                _fullTextResults.value = matches
                _results.value = txtBooks.filter { it.id in matches }
            } finally {
                _isSearching.value = false
            }
        }
    }

    // 可见性过滤：密码文件夹的书不出现在搜索结果里（隐身模式下当前密码绑定的除外）
    private fun visibleFilter(folderId: Long?): (Book) -> Boolean {
        val pwdMap = FolderContentCache.pwdMap.value
        val secret = session.secretPassword.value
        val hiddenFolderIds = pwdMap
            .filter { (_, pwd) -> pwd != FolderContentCache.UNENCRYPTED && pwd != secret }
            .keys
        return { b ->
            (b.folderId == null || b.folderId !in hiddenFolderIds) &&
                (folderId == null || b.folderId == folderId)
        }
    }
}
