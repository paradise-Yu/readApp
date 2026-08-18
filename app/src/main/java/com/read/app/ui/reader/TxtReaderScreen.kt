package com.read.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val readMode by viewModel.readMode.collectAsState()
    val savedRatio by viewModel.savedRatio.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val theme = readerThemes[bgColorIndex.coerceIn(0, readerThemes.lastIndex)]

    // 按空行分段，段间留白，缓解连续阅读疲劳
    val paragraphs = remember(content) {
        content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    }

    // 位置记忆：内容渲染完成后恢复到上次阅读位置
    var restored by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(content, savedRatio) {
        if (!restored && content.isNotEmpty() && savedRatio > 0f) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.scrollTo((savedRatio * scrollState.maxValue).toInt())
        }
        restored = true
    }

    // 退出时保存当前阅读位置（比例 0~1）
    DisposableEffect(bookId) {
        onDispose {
            if (scrollState.maxValue > 0) {
                val ratio = scrollState.value.toFloat() / scrollState.maxValue
                viewModel.saveProgress(bookId, ratio.toString())
            }
        }
    }

    val progress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue
    } else 0f

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(theme.bg)
        ) {
            // 正文：分段渲染，衬线字体 + 宽松行距 + 段间距
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
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

            // 左右翻页模式：点左半屏上一页、右半屏下一页，左右滑动也可翻页
            if (readMode == ReadMode.PAGE && scrollState.maxValue > 0) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pagePx = with(LocalDensity.current) { maxHeight.toPx() }
                    var dragStart = 0
                    var dragAccum by remember { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pagePx) {
                                detectTapGestures { offset ->
                                    val target = if (offset.x < size.width / 2) {
                                        (scrollState.value - pagePx).toInt().coerceAtLeast(0)
                                    } else {
                                        (scrollState.value + pagePx).toInt().coerceAtMost(scrollState.maxValue)
                                    }
                                    scope.launch { scrollState.animateScrollTo(target) }
                                }
                                detectHorizontalDragGestures(
                                    onDragStart = { dragAccum = 0f; dragStart = scrollState.value },
                                    onDragEnd = { dragAccum = 0f }
                                ) { _, dragAmount ->
                                    dragAccum += dragAmount
                                    val target = (dragStart - dragAccum).toInt()
                                        .coerceIn(0, scrollState.maxValue)
                                    scope.launch { scrollState.scrollTo(target) }
                                }
                            }
                    )
                }
            }

            // 右侧滚动条：可拖动跳转
            if (scrollState.maxValue > 0) {
                ReaderScrollBar(
                    scrollState = scrollState,
                    handleColor = theme.text,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            // 底部进度条：百分比 + 进度指示
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = theme.text.copy(alpha = 0.05f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = theme.text.copy(alpha = 0.6f)
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(3.dp),
                        color = theme.text.copy(alpha = 0.45f),
                        trackColor = theme.text.copy(alpha = 0.1f)
                    )
                    if (readMode == ReadMode.PAGE) {
                        Text(
                            text = "翻页模式",
                            fontSize = 11.sp,
                            color = theme.text.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    // 阅读设置弹窗
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("阅读设置") },
            text = {
                Column {
                    Text("字体大小: ${fontSize.toInt()}sp")
                    Slider(
                        value = fontSize,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 14f..32f,
                        steps = 8
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("阅读背景")
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
                                Text(t.name, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("翻页方式")
                        TextButton(onClick = { viewModel.toggleReadMode() }) {
                            Text("${readMode.label} ⇄")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("完成") }
            }
        )
    }
}

// 可拖动的竖向滚动条
@Composable
private fun ReaderScrollBar(
    scrollState: androidx.compose.foundation.ScrollState,
    handleColor: Color,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(28.dp)) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val handlePx = with(density) { 56.dp.toPx() }
        val range = scrollState.maxValue.toFloat()
        val handleY = if (range > 0f) {
            (scrollState.value / range) * (trackPx - handlePx)
        } else 0f

        // 轨道
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .background(handleColor.copy(alpha = 0.08f))
        )
        // 滑块：拖动时按比例跳转正文
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { handleY.toDp() })
                .width(8.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(handleColor.copy(alpha = 0.45f))
                .pointerInput(trackPx, handlePx, range) {
                    var startValue = 0
                    detectDragGestures(
                        onDragStart = { startValue = scrollState.value },
                        onDrag = { change, drag ->
                            change.consume()
                            if (range > 0f) {
                                val target = startValue + drag.y / (trackPx - handlePx) * range
                                scope.launch { scrollState.scrollTo(target.toInt().coerceIn(0, scrollState.maxValue)) }
                            }
                        }
                    )
                }
        )
    }
}
