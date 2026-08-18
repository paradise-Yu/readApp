package com.read.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Book
import com.read.app.domain.model.Bookmark
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.BookmarkRepository
import com.read.app.util.AppScope
import com.read.app.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 阅读模式：上下滚动 / 左右翻页
enum class ReadMode(val label: String) {
    SCROLL("上下滚动"),
    PAGE("左右翻页")
}

// 目录章节：title 为章节名，paragraphIndex 为正文分段下标（用于跳转）
data class Chapter(val title: String, val index: Int)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        // 常见中文章节标题正则（网络小说通用模式）：
        // 第X章/卷/节/回/话/集/部（数字支持阿拉伯与中文数字）、
        // 序章/楔子/引子/尾声/后记/番外/终章等特殊卷首、英文 Chapter/Part
        val CHAPTER_REGEX = Regex(
            "^\\s*(?:" +
                "第\\s*[0-9一二三四五六七八九十百千万两〇零]+\\s*[章卷节回话集部]" +
                "|(?:序章|序言|序曲|前言|引子|楔子|尾声|后记|番外|终章|结局)" +
                "|[Cc]hapter\\s+[0-9]+" +
                "|[Pp]art\\s+[0-9]+" +
            ")"
        )
    }

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

    // 上次阅读位置：段落索引 + 段落内像素偏移（段落可能整章一段，仅存索引会回到段首）
    private val _savedIndex = MutableStateFlow(0)
    val savedIndex: StateFlow<Int> = _savedIndex.asStateFlow()

    private val _savedOffset = MutableStateFlow(0)
    val savedOffset: StateFlow<Int> = _savedOffset.asStateFlow()

    // 由正文解析出的章节目录
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    // 当前书籍的书签列表
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private var bookmarkJob: Job? = null

    fun toggleReadMode() {
        _readMode.value = if (_readMode.value == ReadMode.SCROLL) ReadMode.PAGE else ReadMode.SCROLL
    }

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            _book.value = book
            // 进度格式："段落索引:段内像素偏移"（兼容旧版纯索引格式）
            val saved = book?.readProgress.orEmpty()
            val parts = saved.split(':')
            _savedIndex.value = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            _savedOffset.value = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
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
                    _chapters.value = withContext(Dispatchers.IO) { buildChapters(text) }
                }
                // 订阅本书书签（切换书籍时先取消旧订阅）
                bookmarkJob?.cancel()
                bookmarkJob = viewModelScope.launch {
                    bookmarkRepository.getBookmarks(bookId).collect { _bookmarks.value = it }
                }
            }
        }
    }

    // 按空行分段（与 UI 渲染规则保持一致），再用正则提取章节目录
    private fun buildChapters(text: String): List<Chapter> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        return paragraphs.mapIndexedNotNull { index, para ->
            val firstLine = para.lineSequence().firstOrNull()?.trim() ?: ""
            if (firstLine.isNotEmpty() && firstLine.length <= 40 && CHAPTER_REGEX.containsMatchIn(firstLine)) {
                Chapter(title = firstLine, index = index)
            } else null
        }.take(3000) // 防止极端文件撑爆 UI
    }

    // 在指定段落添加书签
    fun addBookmark(bookId: Long, paragraphIndex: Int, paragraphText: String) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                Bookmark(bookId = bookId, paragraphIndex = paragraphIndex, snippet = paragraphText.take(30))
            )
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun setBgColor(index: Int) {
        _bgColorIndex.value = index
    }

    // 保存当前段落索引与段内偏移，下次打开自动恢复
    // 退出时 ViewModel 可能已被销毁（viewModelScope 已取消），必须用应用级 scope 写库
    fun saveProgress(bookId: Long, paragraphIndex: Int, scrollOffset: Int) {
        AppScope.io.launch {
            bookRepository.updateReadProgress(bookId, System.currentTimeMillis(), "$paragraphIndex:$scrollOffset")
        }
    }
}
