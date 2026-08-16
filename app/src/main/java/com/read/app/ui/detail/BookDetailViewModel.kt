package com.read.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Tag
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            _book.value = bookRepository.getBookById(bookId)
        }
    }

    fun updateRating(bookId: Long, rating: Int) {
        viewModelScope.launch {
            bookRepository.updateRating(bookId, rating)
            loadBook(bookId)
        }
    }

    fun addTag(bookId: Long, tagName: String, color: Int) {
        viewModelScope.launch {
            val existingTags = tagRepository.getAllTagsOnce()
            val tag = existingTags.find { it.name == tagName }
            val tagId = tag?.id ?: tagRepository.insertTag(Tag(name = tagName, color = color))
            tagRepository.addTagToBook(bookId, tagId)
            loadBook(bookId)
        }
    }

    fun removeTag(bookId: Long, tagId: Long) {
        viewModelScope.launch {
            tagRepository.removeTagFromBook(bookId, tagId)
            loadBook(bookId)
        }
    }
}
