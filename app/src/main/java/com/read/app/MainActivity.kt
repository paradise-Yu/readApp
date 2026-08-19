package com.read.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.read.app.ui.theme.ReadAppTheme
import com.read.app.ui.navigation.ReadNavHost
import com.read.app.ui.navigation.NavEvents
import com.read.app.util.ExternalFileHandler
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bookRepository: BookRepository
    @Inject lateinit var folderRepository: FolderRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理外部打开的文件（其他应用选择“用本应用打开”）
        handleExternalIntent(intent)

        setContent {
            ReadAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReadNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.type?.startsWith("text/") == true) {
            val uri = intent.data ?: return
            val sourcePackage = intent.getStringExtra("android.intent.extra.REFERRER_PACKAGE")
                ?: callingActivity?.packageName ?: "Unknown"

            CoroutineScope(Dispatchers.Main).launch {
                val bookId = ExternalFileHandler.handleExternalFile(
                    context = this@MainActivity,
                    uri = uri,
                    sourcePackage = sourcePackage,
                    bookRepository = bookRepository,
                    folderRepository = folderRepository
                )
                if (bookId != null) {
                    // 查询书籍格式以导航到正确的阅读器
                    val book = bookRepository.getBookById(bookId)
                    if (book != null) {
                        NavEvents.openBook(bookId, book.format)
                    }
                }
            }
        }
    }
}
