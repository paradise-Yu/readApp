package com.read.app.ui.reader

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.data.parser.EpubParser
import com.read.app.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // 后台解析 EPUB（SAF 文档先复制到缓存目录，ZipFile 才能读）
    var styledHtml by remember(bookId) { mutableStateOf<String?>(null) }
    LaunchedEffect(book?.filePath) {
        val path = book?.filePath ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val file = FileUtil.resolveFile(context, path)
            val content = file?.let { EpubParser().getHtmlContent(it) } ?: ""
            styledHtml = """
                <html>
                <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    /* 护眼羊皮纸配色：避免纯白底的眩光 */
                    html { background: #F5EFDF; }
                    body { 
                        font-family: serif; 
                        font-size: 18px; 
                        line-height: 1.9; 
                        padding: 16px 20px; 
                        color: #3B3226;
                        background: #F5EFDF;
                    }
                    p { margin: 0 0 0.8em 0; text-align: justify; }
                    img { max-width: 100%; height: auto; }
                    a { color: #6F4E37; }
                    @media (prefers-color-scheme: dark) {
                        html, body { background: #232120; color: #D6CDC0; }
                        a { color: #D7B49A; }
                    }
                </style>
                </head>
                <body>$content</body>
                </html>
            """.trimIndent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "EPUB 阅读") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val html = styledHtml
        if (html != null) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
