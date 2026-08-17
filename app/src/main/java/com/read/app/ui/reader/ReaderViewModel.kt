package com.read.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.repository.BookRepository
import com.read.app.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _fontSize = MutableStateFlow(19f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _bgColorIndex = MutableStateFlow(0)
    val bgColorIndex: StateFlow<Int> = _bgColorIndex.asStateFlow()

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            _book.value = book
            book?.let {
                bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), null)
                // TXT 才需要读取文本内容；PDF/EPUB 由各自渲染组件直接读文件
                if (book.format.lowercase() == "txt") {
                    val text = withContext(Dispatchers.IO) {
                        try {
                            FileUtil.readTextAutoCharset(context, book.filePath)
                        } catch (_: Exception) { "" }
                    }
                    _content.value = text
                }
            }
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun setBgColor(index: Int) {
        _bgColorIndex.value = index
    }

    fun saveProgress(bookId: Long, progress: String) {
        viewModelScope.launch {
            bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), progress)
        }
    }
}
