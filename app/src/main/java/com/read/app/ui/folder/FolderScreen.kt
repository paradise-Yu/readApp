package com.read.app.ui.folder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.read.app.domain.model.Folder
import com.read.app.util.FileUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val inSecretMode by viewModel.inSecretMode.collectAsState()
    val folderPasswords by viewModel.folderPasswords.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // lastPathSegment 形如 "primary:Books"，取冒号后部分作为显示名
            val name = it.lastPathSegment?.substringAfterLast(':') ?: "文件夹"
            viewModel.addFolder(it, name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (inSecretMode) "文件夹管理 · 隐身模式" else "文件夹管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 隐身模式下提供一键退出：回到普通书架
                    if (inSecretMode) {
                        IconButton(onClick = {
                            viewModel.exitSecretMode()
                            onBackClick()
                        }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "退出隐身模式")
                        }
                    }
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "添加文件夹")
                    }
                }
            )
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无文件夹，点击右上角添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
                    FolderItem(
                        folder = folder,
                        currentPassword = folderPasswords[folder.id],
                        onDelete = { viewModel.deleteFolder(folder) },
                        onRename = { name -> viewModel.renameFolder(folder, name) },
                        onSetPassword = { pwd -> viewModel.setFolderPassword(folder, pwd) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: Folder,
    currentPassword: String?,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onSetPassword: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var passwordInput by remember(folder.id) { mutableStateOf("") }
    var renameInput by remember(folder.id) { mutableStateOf("") }

    Box(modifier = Modifier.combinedClickable(
        onClick = {},
        onLongClick = {
            passwordInput = currentPassword ?: ""
            showPasswordDialog = true
        }
    )) {
        ListItem(
            headlineContent = { Text(folder.displayName) },
            leadingContent = {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Row {
                    // 重命名书架显示名（只改数据库，不动磁盘目录）
                    IconButton(onClick = {
                        renameInput = folder.displayName
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除文件夹") },
            text = { Text("确定要删除文件夹 \"${folder.displayName}\" 吗？\n(不会删除文件，仅从应用中移除)") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    // 重命名书架：只修改应用内显示名，磁盘目录名不变
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名书架") },
            text = {
                Column {
                    Text(
                        "只修改应用内的书架显示名，磁盘上的目录名不会改变。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("书架名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(renameInput)
                    showRenameDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    // 长按设置访问密码：密码以明文文件（密码.txt）保存在该文件夹内，设后隐藏，删文件即解除
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("设置访问密码") },
            text = {
                Column {
                    Text(
                        "设置后密码将以明文文件（${FileUtil.PASSWORD_FILE_NAME}）保存在该文件夹内，" +
                            "文件夹在普通列表中隐藏，输入密码才能进入。\n" +
                            "如需解除隐藏，直接删除文件夹内的密码文件，或在下方留空保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetPassword(passwordInput)
                    showPasswordDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("取消") }
            }
        )
    }
}
