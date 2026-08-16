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
                            body { 
                                font-family: serif; 
                                font-size: 18px; 
                                line-height: 1.8; 
                                padding: 16px; 
                                color: #333;
                            }
                            img { max-width: 100%; height: auto; }
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
