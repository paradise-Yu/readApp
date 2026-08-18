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

// 阅读模式：上下滚动 / 左右翻页
enum class ReadMode(val label: String) {
    SCROLL("上下滚动"),
    PAGE("左右翻页")
}

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

    private val _readMode = MutableStateFlow(ReadMode.SCROLL)
    val readMode: StateFlow<ReadMode> = _readMode.asStateFlow()

    // 上次阅读的位置比例（0~1），用于打开时恢复
    private val _savedRatio = MutableStateFlow(0f)
    val savedRatio: StateFlow<Float> = _savedRatio.asStateFlow()

    fun toggleReadMode() {
        _readMode.value = if (_readMode.value == ReadMode.SCROLL) ReadMode.PAGE else ReadMode.SCROLL
    }

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            _book.value = book
            _savedRatio.value = book?.readProgress?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            book?.let {
                // 只更新阅读时间，保留原进度（退出时才写入新进度）
                bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), book.readProgress)
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
