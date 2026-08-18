package com.read.app.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.repository.TagRepository
import com.read.app.domain.session.ReaderSession
import com.read.app.util.FileUtil
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

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun performSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val q = _query.value
            val tag = _selectedTag.value

            if (q.isBlank() && tag == null) {
                _results.value = emptyList()
                _fullTextResults.value = emptyMap()
                return@launch
            }

            // 含密码文件的文件夹中的书不出现在搜索结果里（隐身模式下当前密码绑定的除外）
            val folders = folderRepository.getAllFolders().first()
            val pwdMap = withContext(Dispatchers.IO) {
                folders.associate { it.id to FileUtil.readFolderPassword(context, it.path) }
            }
            val secret = session.secretPassword.value
            val hiddenFolderIds = folders
                .filter { pwdMap[it.id] != null && pwdMap[it.id] != secret }
                .map { it.id }.toSet()
            fun List<Book>.visible() = filter { it.folderId == null || it.folderId !in hiddenFolderIds }

            // Search by tag if selected
            if (tag != null && q.isBlank()) {
                bookRepository.searchBooksByTag(tag).collect {
                    _results.value = it.visible()
                }
                return@launch
            }

            // Search by text (title/author)
            if (q.isNotBlank() && tag == null) {
                bookRepository.searchBooks(q).collect { rawBooks ->
                    val books = rawBooks.visible()
                    _results.value = books
                    // Full-text search in TXT files
                    val fullTextMatches = mutableMapOf<Long, List<String>>()
                    books.filter { it.format == "txt" }.forEach { book ->
                        withContext(Dispatchers.IO) {
                            try {
                                val content = FileUtil.readTextAutoCharset(context, book.filePath)
                                val lines = content.lines()
                                val matches = lines.filter { line ->
                                    line.contains(q, ignoreCase = true)
                                }.take(3)
                                if (matches.isNotEmpty()) {
                                    fullTextMatches[book.id] = matches
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    _fullTextResults.value = fullTextMatches
                }
                return@launch
            }

            // Combined: search by text AND filter by tag
            if (q.isNotBlank() && tag != null) {
                bookRepository.searchBooks(q).collect { rawBooks ->
                    _results.value = rawBooks.visible().filter { book ->
                        book.tags.any { it.name == tag }
                    }
                }
            }
        }
    }
}
