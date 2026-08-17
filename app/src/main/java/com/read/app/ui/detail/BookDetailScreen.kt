package com.read.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.ui.components.RatingBar
import com.read.app.ui.components.TagChip
import com.read.app.util.FileUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    var showAddTagDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书籍详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        book?.let { b ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Book info
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(b.title, style = MaterialTheme.typography.headlineSmall)
                        if (b.author != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("作者: ${b.author}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("格式: ${b.format.uppercase()}", style = MaterialTheme.typography.bodySmall)
                        Text("大小: ${FileUtil.formatFileSize(b.fileSize)}", style = MaterialTheme.typography.bodySmall)
                        Text("路径: ${b.filePath}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }

                // Rating
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("评分", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        RatingBar(
                            rating = b.rating ?: 0,
                            starSize = 32.dp,
                            onRatingChange = { viewModel.updateRating(b.id, it) }
                        )
                    }
                }

                // Tags
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("标签", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { showAddTagDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "添加标签")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (b.tags.isEmpty()) {
                            Text("暂无标签", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                b.tags.forEach { tag ->
                                    var showRemove by remember { mutableStateOf(false) }
                                    if (showRemove) {
                                        AlertDialog(
                                            onDismissRequest = { showRemove = false },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    viewModel.removeTag(b.id, tag.id)
                                                    showRemove = false
                                                }) { Text("移除") }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showRemove = false }) { Text("取消") }
                                            },
                                            title = { Text("移除标签") },
                                            text = { Text("确定要移除标签 \"${tag.name}\" 吗？") }
                                        )
                                    }
                                    TagChip(
                                        name = tag.name,
                                        color = tag.color,
                                        onClick = { showRemove = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTagDialog) {
        AddTagDialog(
            existingTags = allTags,
            onDismiss = { showAddTagDialog = false },
            onConfirm = { name, color ->
                book?.let { viewModel.addTag(it.id, name, color) }
                showAddTagDialog = false
            }
        )
    }
}

@Composable
private fun AddTagDialog(
    existingTags: List<com.read.app.domain.model.Tag>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(0xFF4CAF50.toInt()) }

    val colors = listOf(
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
        0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
        0xFFFF5722.toInt(), 0xFF607D8B.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("标签名称") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Text("选择颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color }
                                .then(
                                    if (color == selectedColor) Modifier.background(
                                        Color.White.copy(alpha = 0.3f), CircleShape
                                    ) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (tagName.isNotBlank()) onConfirm(tagName.trim(), selectedColor) },
                enabled = tagName.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
