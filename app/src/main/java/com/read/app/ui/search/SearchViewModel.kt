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
import kotlinx.coroutines.delay
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

    private var searchJob: Job? = null

    fun setQuery(newQuery: String) {
        _query.value = newQuery
        performSearch()
    }

    fun setSelectedTag(tagName: String?) {
        _selectedTag.value = tagName
        performSearch()
    }

    fun setSelectedFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
        performSearch()
    }

    private fun performSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val q = _query.value.trim()
            val tag = _selectedTag.value
            val folderId = _selectedFolderId.value

            if (q.isBlank() && tag == null && folderId == null) {
                _results.value = emptyList()
                _fullTextResults.value = emptyMap()
                return@launch
            }

            // 可见性过滤：密码文件夹的书不出现在搜索结果里（隐身模式下当前密码绑定的除外）
            // 密码从共享缓存读取，与书架保持一致
            val pwdMap = FolderContentCache.pwdMap.value
            val secret = session.secretPassword.value
            val hiddenFolderIds = pwdMap
                .filter { (_, pwd) ->
                    pwd != FolderContentCache.UNENCRYPTED && pwd != secret
                }.keys
            fun List<Book>.visible() = filter { b ->
                (b.folderId == null || b.folderId !in hiddenFolderIds) &&
                    (folderId == null || b.folderId == folderId)
            }

            // 仅标签筛选
            if (tag != null && q.isBlank()) {
                bookRepository.searchBooksByTag(tag).collect {
                    _results.value = it.visible()
                }
                return@launch
            }

            if (q.isNotBlank()) {
                // 1. 书名/作者匹配
                val titleMatches = bookRepository.searchBooks(q).first().visible()
                    .let { books ->
                        if (tag != null) books.filter { b -> b.tags.any { it.name == tag } }
                        else books
                    }

                // 2. 书内全文搜索（TXT）：独立于书名匹配，正文含关键词的书也会命中
                val fullTextMatches = mutableMapOf<Long, List<String>>()
                val txtBooks = bookRepository.getAllBooks().first().visible()
                    .filter { it.format.lowercase() == "txt" }
                txtBooks.forEach { book ->
                    val matches = withContext(Dispatchers.IO) {
                        try {
                            val content = FileUtil.readTextAutoCharset(context, book.filePath)
                            content.lines().filter { it.contains(q, ignoreCase = true) }.take(3)
                        } catch (_: Exception) { emptyList() }
                    }
                    if (matches.isNotEmpty()) fullTextMatches[book.id] = matches
                }
                _fullTextResults.value = fullTextMatches

                // 3. 合并：书名匹配 + 正文匹配（去重，书名匹配优先展示）
                val titleIds = titleMatches.map { it.id }.toSet()
                val contentOnlyMatches = txtBooks.filter {
                    it.id in fullTextMatches && it.id !in titleIds
                }
                _results.value = titleMatches + contentOnlyMatches
            } else {
                // 仅文件夹筛选：列出该文件夹下所有可见书
                _results.value = bookRepository.getAllBooks().first().visible()
                _fullTextResults.value = emptyMap()
            }
        }
    }
}
