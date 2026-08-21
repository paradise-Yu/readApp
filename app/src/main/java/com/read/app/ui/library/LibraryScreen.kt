package com.read.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.domain.model.Book
import com.read.app.ui.components.BookCover
import com.read.app.ui.components.TagChip
import com.read.app.ui.components.RatingBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onFolderClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val scanMessage by viewModel.scanMessage.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val folders by viewModel.visibleFolders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTagName by viewModel.selectedTagName.collectAsState()
    val inSecretMode by viewModel.inSecretMode.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showSecretDialog by remember { mutableStateOf(false) }
    var secretInput by remember { mutableStateOf("") }
    // 长按书籍弹出的操作菜单（查看详情 / 移动到书架）
    var menuBook by remember { mutableStateOf<Book?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast(':') ?: "Unknown"
            viewModel.scanFolder(it, it.toString(), name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            inSecretMode -> "书架 · 隐身模式"
                            selectedFolderId != null ->
                                "书架 · ${folders.firstOrNull { it.id == selectedFolderId }?.displayName ?: ""}"
                            else -> "书架"
                        }
                    )
                },
                actions = {
                    // 隐身模式下顶栏直接提供退出入口，一键回到普通书架
                    if (inSecretMode) {
                        IconButton(onClick = { viewModel.exitSecretMode() }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "退出隐身模式")
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (order == sortOrder) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "切换视图"
                        )
                    }
                    IconButton(onClick = onFolderClick) {
                        Icon(Icons.Default.Folder, contentDescription = "文件夹管理")
                    }
                    // 文件夹切换：点击弹下拉，长按输入密码进入隐身模式
                    Box {
                        Icon(
                            if (inSecretMode) Icons.Default.Lock else Icons.Default.FilterList,
                            contentDescription = "文件夹切换",
                            tint = if (inSecretMode) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { showFolderMenu = true },
                                    onLongClick = {
                                        secretInput = ""
                                        showSecretDialog = true
                                    }
                                )
                                .padding(12.dp)
                        )
                        DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("全部书籍") },
                                onClick = {
                                    viewModel.selectFolder(null)
                                    showFolderMenu = false
                                },
                                leadingIcon = {
                                    if (selectedFolderId == null) Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.displayName) },
                                    onClick = {
                                        viewModel.selectFolder(folder.id)
                                        showFolderMenu = false
                                    },
                                    leadingIcon = {
                                        if (selectedFolderId == folder.id) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                            if (inSecretMode) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("退出隐身模式") },
                                    onClick = {
                                        viewModel.exitSecretMode()
                                        showFolderMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加文件夹")
                    }
                }
            )
        },
        snackbarHost = {
            scanMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearScanMessage() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 标签筛选行：点击公共标签筛书，再点取消
        if (allTags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(allTags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = selectedTagName == tag.name,
                        onClick = { viewModel.setTagFilter(tag.name) },
                        label = { Text(tag.name, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.color))
                            )
                        }
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "书架为空，点击右上角 + 添加文件夹",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookGridItem(book = book, onClick = { onBookClick(book) }, onLongClick = { menuBook = book })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookListItem(book = book, onClick = { onBookClick(book) }, onLongClick = { menuBook = book })
                }
            }
        }
        } // 内容 Box
        } // 标签列 Column
        } // PullToRefreshBox
    }

    // 长按书籍操作菜单：查看详情 / 移动到其它书架（物理移动文件 + 更新数据库）
    menuBook?.let { book ->
        val moveTargets = folders.filter { it.id != book.folderId }
        AlertDialog(
            onDismissRequest = { menuBook = null },
            title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        menuBook = null
                        onBookLongClick(book)
                    }) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("查看详情")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "移动到书架",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (moveTargets.isEmpty()) {
                        Text(
                            "没有其它书架可移动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        moveTargets.forEach { folder ->
                            TextButton(onClick = {
                                viewModel.moveBookToFolder(book, folder)
                                menuBook = null
                            }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(folder.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuBook = null }) { Text("取消") }
            }
        )
    }

    // 隐身模式密码输入框
    if (showSecretDialog) {
        AlertDialog(
            onDismissRequest = { showSecretDialog = false },
            title = { Text("输入访问密码") },
            text = {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (secretInput.isNotEmpty()) viewModel.enterSecretMode(secretInput)
                        showSecretDialog = false
                    }
                ) { Text("进入") }
            },
            dismissButton = {
                TextButton(onClick = { showSecretDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BookGridItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        BookCover(
            title = book.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (book.author != null) {
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if ((book.rating ?: 0) > 0) {
            Spacer(Modifier.height(2.dp))
            RatingBar(rating = book.rating ?: 0, starSize = 12.dp)
        }
        if (book.tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                book.tags.take(2).forEach { tag ->
                    TagChip(name = tag.name, color = tag.color)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (book.format.lowercase()) {
                    "pdf" -> Icons.Default.PictureAsPdf
                    "epub" -> Icons.Default.Book
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (book.author != null) {
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.format.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (book.fileSize > 0) {
                        Text(formatFileSize(book.fileSize), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (book.rating != null) {
                RatingBar(rating = book.rating ?: 0, starSize = 14.dp)
            }
        }
    }
}

// 文件大小格式化：字节 → KB/MB/GB
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
        else -> "${bytes / 1024 / 1024 / 1024} GB"
    }
}
