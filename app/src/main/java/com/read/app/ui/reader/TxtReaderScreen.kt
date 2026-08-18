package com.read.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val savedIndex by viewModel.savedIndex.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    // 目录/书签面板：null=关闭，0=目录页，1=书签页
    var sheetTab by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val theme = readerThemes[bgColorIndex.coerceIn(0, readerThemes.lastIndex)]

    // 按空行分段，段间留白，缓解连续阅读疲劳
    val paragraphs = remember(content) {
        content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    }

    // 当前阅读到的段落索引（滚动时自动更新）
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // 位置记忆：内容加载完成后恢复到上次阅读段落
    // 注意：必须在内容就绪后才置 restored=true，否则首帧空内容会提前消耗恢复机会
    var restored by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(content, savedIndex) {
        if (!restored && content.isNotEmpty()) {
            if (savedIndex > 0) {
                listState.scrollToItem(savedIndex.coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0)))
            }
            restored = true
        }
    }

    // 退出时保存当前段落索引
    DisposableEffect(bookId) {
        onDispose {
            if (paragraphs.isNotEmpty()) {
                viewModel.saveProgress(bookId, listState.firstVisibleItemIndex)
            }
        }
    }

    val progress = if (paragraphs.size > 1) {
        currentIndex.toFloat() / (paragraphs.size - 1)
    } else 0f

    // 当前所在章节（最后一个起始位置不超过当前段落的章节）
    val currentChapter by remember {
        derivedStateOf { chapters.lastOrNull { it.index <= currentIndex } }
    }

    val bookmarkIndices by remember {
        derivedStateOf { bookmarks.map { it.paragraphIndex }.toSet() }
    }

    Scaffold(
        containerColor = theme.bg,
        topBar = {
            // 顶栏与阅读背景同色，保证沉浸感
            TopAppBar(
                title = { Text(book?.title ?: "阅读", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 一键添加当前位置书签
                    IconButton(onClick = {
                        if (paragraphs.isNotEmpty()) {
                            viewModel.addBookmark(bookId, currentIndex, paragraphs[currentIndex])
                        }
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "添加书签")
                    }
                    // 目录 / 书签面板
                    IconButton(onClick = { sheetTab = 0 }) {
                        Icon(Icons.Default.ListAlt, contentDescription = "目录")
                    }
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
            // 正文：LazyColumn 分段渲染，支持按段落索引精准跳转（目录/书签/位置记忆）
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                items(paragraphs.size) { index ->
                    Column {
                        // 已加书签的段落显示角标
                        if (index in bookmarkIndices) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = "书签",
                                modifier = Modifier.size(14.dp),
                                tint = theme.text.copy(alpha = 0.55f)
                            )
                        }
                        Text(
                            text = paragraphs[index],
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.9).sp,
                            color = theme.text,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(bottom = (fontSize * 0.8).dp)
                        )
                    }
                }
            }

            // 左右翻页模式：点左半屏上一页、右半屏下一页，左右滑动也可翻页
            if (readMode == ReadMode.PAGE && paragraphs.size > 1) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pagePx = with(LocalDensity.current) { maxHeight.toPx() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pagePx) {
                                detectTapGestures { offset ->
                                    scope.launch {
                                        lazyListAnimatePage(listState, if (offset.x < size.width / 2) -pagePx else pagePx)
                                    }
                                }
                                detectHorizontalDragGestures { _, dragAmount ->
                                    scope.launch { listState.scrollBy(-dragAmount) }
                                }
                            }
                    )
                }
            }

            // 右侧滚动条：可拖动跳转
            if (paragraphs.size > 1) {
                ReaderScrollBar(
                    ratio = progress,
                    handleColor = theme.text,
                    onSeek = { r ->
                        val target = (r * paragraphs.lastIndex).toInt()
                        scope.launch { listState.scrollToItem(target.coerceIn(0, paragraphs.lastIndex)) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            // 底部状态栏：当前章节 + 进度百分比
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
                        text = currentChapter?.title ?: "正文",
                        fontSize = 11.sp,
                        color = theme.text.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = theme.text.copy(alpha = 0.6f)
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

    // 目录 / 书签面板
    if (sheetTab >= 0) {
        ReaderSheet(
            tab = sheetTab,
            onTabChange = { sheetTab = it },
            chapters = chapters,
            bookmarks = bookmarks,
            currentIndex = currentIndex,
            onJump = { index ->
                scope.launch { listState.scrollToItem(index.coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0))) }
                sheetTab = -1
            },
            onAddBookmark = {
                if (paragraphs.isNotEmpty()) viewModel.addBookmark(bookId, currentIndex, paragraphs[currentIndex])
            },
            onDeleteBookmark = { viewModel.removeBookmark(it) },
            onDismiss = { sheetTab = -1 }
        )
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

// 翻页模式下按整页平滑滚动
private suspend fun lazyListAnimatePage(
    listState: androidx.compose.foundation.lazy.LazyListState,
    px: Float
) {
    listState.animateScrollBy(px)
}

// 目录 + 书签面板（底部弹出，两个 Tab）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSheet(
    tab: Int,
    onTabChange: (Int) -> Unit,
    chapters: List<Chapter>,
    bookmarks: List<com.read.app.domain.model.Bookmark>,
    currentIndex: Int,
    onJump: (Int) -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (com.read.app.domain.model.Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { onTabChange(0) }, text = { Text("目录 (${chapters.size})") })
                Tab(selected = tab == 1, onClick = { onTabChange(1) }, text = { Text("书签 (${bookmarks.size})") })
            }

            if (tab == 0) {
                if (chapters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "未识别到章节\n（支持\"第X章/第X节/序章/楔子/Chapter X\"等常见格式）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 自动滚动到当前章节附近
                    val chapterListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = chapters.indexOfLast { it.index <= currentIndex }.coerceAtLeast(0)
                    )
                    LazyColumn(state = chapterListState, modifier = Modifier.height(380.dp)) {
                        items(chapters.size) { i ->
                            val chapter = chapters[i]
                            val isCurrent = chapters.lastOrNull { it.index <= currentIndex }?.index == chapter.index
                            ListItem(
                                headlineContent = {
                                    Text(
                                        chapter.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.clickable { onJump(chapter.index) }
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 在当前阅读位置添加书签
                    TextButton(
                        onClick = onAddBookmark,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("在当前阅读位置添加书签")
                    }
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无书签",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(bookmarks.size) { i ->
                                val bm = bookmarks[i]
                                ListItem(
                                    headlineContent = {
                                        Text(bm.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = { Text("第 ${bm.paragraphIndex + 1} 段") },
                                    leadingContent = {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { onDeleteBookmark(bm) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除书签")
                                        }
                                    },
                                    modifier = Modifier.clickable { onJump(bm.paragraphIndex) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 可拖动的竖向滚动条：ratio 为当前阅读进度（0~1），onSeek 回调拖动目标比例
@Composable
private fun ReaderScrollBar(
    ratio: Float,
    handleColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(28.dp)) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val handlePx = with(density) { 56.dp.toPx() }
        val handleY = ratio * (trackPx - handlePx)

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
                .pointerInput(trackPx, handlePx) {
                    var startY = 0f
                    var startRatio = 0f
                    detectDragGestures(
                        onDragStart = { offset -> startY = offset.y; startRatio = ratio },
                        onDrag = { change, _ ->
                            change.consume()
                            val range = trackPx - handlePx
                            if (range > 0f) {
                                val target = (startRatio + (change.position.y - startY) / range).coerceIn(0f, 1f)
                                onSeek(target)
                            }
                        }
                    )
                }
        )
    }
}
