package com.read.app.ui.folder

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.read.app.domain.model.Folder
import com.read.app.domain.repository.BookRepository
import com.read.app.domain.repository.FolderRepository
import com.read.app.domain.session.ReaderSession
import com.read.app.util.BookImporter
import com.read.app.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val bookRepository: BookRepository,
    private val session: ReaderSession,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 密码以明文文件形式存放在各文件夹内，文件为唯一可信来源（手工删除文件即解除隐藏）
    private val _pwdMap = MutableStateFlow<Map<Long, String?>>(emptyMap())
    val folderPasswords: StateFlow<Map<Long, String?>> = _pwdMap.asStateFlow()

    init {
        // 文件夹变化时重新读取密码文件（含用户手工删除文件的情形）
        viewModelScope.launch {
            folderRepository.getAllFolders().collect { folders ->
                val map = withContext(Dispatchers.IO) {
                    folders.associate { it.id to FileUtil.readFolderPassword(context, it.path) }
                }
                _pwdMap.value = map
            }
        }
    }

    // 普通模式只显示未设密码的文件夹；隐身模式只显示绑定当前密码的
    val folders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword,
        _pwdMap
    ) { all, pwd, pwdMap ->
        if (pwd == null) all.filter { pwdMap[it.id] == null }
        else all.filter { pwdMap[it.id] == pwd }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inSecretMode: StateFlow<Boolean> = session.secretPassword
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun addFolder(uri: Uri, name: String) {
        viewModelScope.launch {
            // 持久化 URI 权限，保证重启后仍可读取
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val folderId = folderRepository.insertFolder(Folder(path = uri.toString(), name = name))
            BookImporter.scanAndImport(context, bookRepository, uri.toString(), folderId)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folder)
        }
    }

    // 设置/清除访问密码：密码写入文件夹内的明文文件；空密码 = 删除密码文件
    fun setFolderPassword(folder: Folder, password: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileUtil.writeFolderPassword(context, folder.path, password.ifBlank { null })
            }
            if (ok) {
                _pwdMap.value = _pwdMap.value + (folder.id to password.ifBlank { null })
            }
        }
    }

    // 退出隐身模式，回到普通列表
    fun exitSecretMode() {
        session.exitSecretMode()
    }
}
