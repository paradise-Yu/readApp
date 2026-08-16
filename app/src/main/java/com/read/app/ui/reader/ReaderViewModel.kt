package com.read.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.data.parser.ParserFactory
import com.read.app.domain.model.Book
import com.read.app.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _fontSize = MutableStateFlow(18f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            _book.value = book
            book?.let {
                bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), null)
                withContext(Dispatchers.IO) {
                    val file = File(book.filePath)
                    if (file.exists()) {
                        val parser = ParserFactory.getParser(book.format)
                        _content.value = parser.readContent(file)
                    }
                }
            }
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun saveProgress(bookId: Long, progress: String) {
        viewModelScope.launch {
            bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), progress)
        }
    }
}
