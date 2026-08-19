package com.read.app.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 全局导航事件：外部打开文件后，通知 NavHost 跳转到阅读器
data class PendingBook(val bookId: Long, val format: String)

object NavEvents {
    private val _pendingBook = MutableStateFlow<PendingBook?>(null)
    val pendingBook: StateFlow<PendingBook?> = _pendingBook

    fun openBook(bookId: Long, format: String) {
        _pendingBook.value = PendingBook(bookId, format)
    }

    fun clearPendingBook() {
        _pendingBook.value = null
    }
}
