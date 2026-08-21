package com.read.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = { Text("搜索书名、作者、内容...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(8.dp)
        ) {
            // 文件夹筛选 chips
            if (folders.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFolderId == null,
                                onClick = { viewModel.setSelectedFolder(null) },
                                label = { Text("全部文件夹") }
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
                                label = { Text(folder.displayName) }
                            )
                        }
                    }
                }
            }

            // Tag filter chips
            if (allTags.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { viewModel.setSelectedTag(null) },
                                label = { Text("全部") }
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag.name,
                                onClick = { viewModel.setSelectedTag(if (selectedTag == tag.name) null else tag.name) },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }

            // Search results
            items(results) { book ->
                Column {
                    ListItem(
                        headlineContent = { Text(book.title) },
                        supportingContent = {
                            Column {
                                Text("${book.format.uppercase()} · ${book.author ?: "未知作者"}")
                                // Show full-text search matches
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
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { onBookClick(book) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
