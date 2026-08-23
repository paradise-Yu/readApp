package com.read.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBookClick: (Book) -> Unit,
    onBackClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val fullTextResults by viewModel.fullTextResults.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                // 自定义顶栏需手动补上状态栏留白（TopAppBar 默认自带）
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // 第一行：返回 + 搜索框 + 搜索按钮
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { viewModel.setQuery(it) },
                            placeholder = { Text("输入书名或作者") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "清空")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                viewModel.searchByName()
                            }),
                            // 点击别处（输入框失焦）即开始文件名搜索
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { state ->
                                    if (!state.isFocused && query.isNotBlank()) {
                                        viewModel.searchByName()
                                    }
                                }
                        )
                        Spacer(Modifier.width(4.dp))
                        // 内容搜索：扫描所有 TXT 正文（较慢，手动触发）
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.searchContent()
                            },
                            enabled = query.isNotBlank() && !isSearching,
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.ManageSearch, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("搜内容")
                        }
                    }

                    // 第二行：筛选条件
                    if (folders.size > 1 || allTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (folders.size > 1) {
                                    item {
                                        FilterChip(
                                            selected = selectedFolderId == null,
                                            onClick = { viewModel.setSelectedFolder(null) },
                                            label = { Text("全部书架", style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                    items(folders) { folder ->
                                        FilterChip(
                                            selected = selectedFolderId == folder.id,
                                            onClick = {
                                                viewModel.setSelectedFolder(
                                                    if (selectedFolderId == folder.id) null else folder.id
                                                )
                                            },
                                            label = { Text(folder.displayName, style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                }
                                items(allTags) { tag ->
                                    FilterChip(
                                        selected = selectedTag == tag.name,
                                        onClick = {
                                            viewModel.setSelectedTag(if (selectedTag == tag.name) null else tag.name)
                                        },
                                        label = { Text(tag.name, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }

                    if (isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // 搜索前的使用提示
            if (!hasSearched && !isSearching) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.ManageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "输入关键词后点击\"搜内容\"搜索正文，\n或点击空白处直接搜索书名",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 无结果提示
            if (hasSearched && !isSearching && results.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "没有找到相关书籍",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 搜索结果
            items(results) { book ->
                Column {
                    ListItem(
                        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text("${book.format.uppercase()} · ${book.author ?: "未知作者"}")
                                // 内容搜索命中行预览
                                fullTextResults[book.id]?.let { matches ->
                                    matches.take(2).forEach { match ->
                                        Text(
                                            text = match.trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                when (book.format.lowercase()) {
                                    "pdf" -> Icons.Default.PictureAsPdf
                                    "epub" -> Icons.Default.Book
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onBookClick(book) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
