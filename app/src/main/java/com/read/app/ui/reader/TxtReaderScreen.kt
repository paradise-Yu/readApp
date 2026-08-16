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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val bgColors = listOf(
    Color(0xFFFFFFFF), // 白色
    Color(0xFFF5F5DC), // 米色
    Color(0xFFC7EDCC), // 绿色护眼
    Color(0xFF2B2B2B), // 深色
    Color(0xFF000000)  // 纯黑
)

private val textColors = listOf(
    Color(0xFF000000), // 白色背景 -> 黑字
    Color(0xFF000000), // 米色背景 -> 黑字
    Color(0xFF000000), // 绿色背景 -> 黑字
    Color(0xFFCCCCCC), // 深色背景 -> 浅字
    Color(0xFFAAAAAA)  // 纯黑背景 -> 浅字
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

    val bgColor = bgColors[bgColorIndex]
    val textColor = textColors[bgColorIndex]

    Scaffold(
        topBar = {
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (showSettings) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("字体大小: ${fontSize.toInt()}sp")
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..36f,
                            steps = 12
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("背景颜色")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            bgColors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { viewModel.setBgColor(index) }
                                        .then(
                                            if (index == bgColorIndex) Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                CircleShape
                                            ) else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = content,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.8).sp,
                color = textColor
            )
        }
    }
}
