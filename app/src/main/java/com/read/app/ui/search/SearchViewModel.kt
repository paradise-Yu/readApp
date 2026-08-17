package com.read.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.data.parser.TxtParser
import com.read.app.domain.model.Book
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tagRepository: TagRepository
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

            // Search by tag if selected
            if (tag != null && q.isBlank()) {
                bookRepository.searchBooksByTag(tag).collect {
                    _results.value = it
                }
                return@launch
            }

            // Search by text (title/author)
            if (q.isNotBlank() && tag == null) {
                bookRepository.searchBooks(q).collect { books ->
                    _results.value = books
                    // Full-text search in TXT files
                    val fullTextMatches = mutableMapOf<Long, List<String>>()
                    books.filter { it.format == "txt" }.forEach { book ->
                        withContext(Dispatchers.IO) {
                            try {
                                val file = File(book.filePath)
                                if (file.exists()) {
                                    val content = TxtParser().readContent(file)
                                    val lines = content.lines()
                                    val matches = lines.filter { line ->
                                        line.contains(q, ignoreCase = true)
                                    }.take(3)
                                    if (matches.isNotEmpty()) {
                                        fullTextMatches[book.id] = matches
                                    }
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
                bookRepository.searchBooks(q).collect { books ->
                    _results.value = books.filter { book ->
                        book.tags.any { it.name == tag }
                    }
                }
            }
        }
    }
}
