package com.read.app.ui.reader

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.data.parser.EpubParser
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

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
        book?.let { b ->
            val file = File(b.filePath)
            if (file.exists()) {
                val htmlContent = remember {
                    val parser = EpubParser()
                    val content = parser.getHtmlContent(file)
                    val styledHtml = """
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
                    styledHtml
                }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = false
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}
