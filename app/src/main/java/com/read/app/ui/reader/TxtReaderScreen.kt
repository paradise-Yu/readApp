package com.read.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// 护眼阅读配色 —— 依据色彩科学调校：
// - 避免纯白底+纯黑字的极端对比（眩光）
// - 深色模式用暖深灰而非纯黑（避免光晕效应）
// - 护眼绿降低饱和度
data class ReaderTheme(val name: String, val bg: Color, val text: Color)

private val readerThemes = listOf(
    ReaderTheme("纸白", Color(0xFFFAF6EF), Color(0xFF3B3226)),  // 暖纸白 + 柔墨黑
    ReaderTheme("羊皮", Color(0xFFF1E3C8), Color(0xFF4A3B2A)),  // 羊皮纸黄
    ReaderTheme("护眼", Color(0xFFD8E6CE), Color(0xFF2F3B28)),  // 低饱和护眼绿
    ReaderTheme("暮色", Color(0xFF232120), Color(0xFFD6CDC0)),  // 暖深灰
    ReaderTheme("夜读", Color(0xFF121212), Color(0xFFBFBAB2))   // 近黑（非纯黑）
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtReaderScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val content by viewModel.content.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val bgColorIndex by viewModel.bgColorIndex.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val theme = readerThemes[bgColorIndex.coerceIn(0, readerThemes.lastIndex)]

    // 按空行分段，段间留白，缓解连续阅读疲劳
    val paragraphs = remember(content) {
        content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    }

    Scaffold(
        containerColor = theme.bg,
        topBar = {
            // 顶栏与阅读背景同色，保证沉浸感
            TopAppBar(
                title = { Text(book?.title ?: "阅读") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.bg,
                    titleContentColor = theme.text,
                    navigationIconContentColor = theme.text,
                    actionIconContentColor = theme.text
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(theme.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (showSettings) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.text.copy(alpha = 0.06f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("字体大小: ${fontSize.toInt()}sp", color = theme.text)
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 14f..32f,
                            steps = 8
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("阅读背景", color = theme.text)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            readerThemes.forEachIndexed { index, t ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(t.bg)
                                            .clickable { viewModel.setBgColor(index) }
                                            .then(
                                                if (index == bgColorIndex) Modifier.background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                    CircleShape
                                                ) else Modifier
                                            )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(t.name, fontSize = 10.sp, color = theme.text)
                                }
                            }
                        }
                    }
                }
            }
            // 分段渲染：衬线字体 + 宽松行距 + 段间距
            paragraphs.forEach { para ->
                Text(
                    text = para,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.9).sp,
                    color = theme.text,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = (fontSize * 0.8).dp)
                )
            }
        }
    }
}
