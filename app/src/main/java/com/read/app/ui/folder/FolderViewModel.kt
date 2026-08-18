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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val bookRepository: BookRepository,
    private val session: ReaderSession,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 普通模式只显示未设密码的文件夹；隐身模式只显示绑定当前密码的
    val folders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword
    ) { all, pwd ->
        if (pwd == null) all.filter { it.password == null }
        else all.filter { it.password == pwd }
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

    // 设置/清除访问密码：空密码 = 取消隐藏
    fun setFolderPassword(folder: Folder, password: String) {
        viewModelScope.launch {
            folderRepository.setFolderPassword(folder.id, password.ifBlank { null })
        }
    }

    // 退出隐身模式，回到普通列表
    fun exitSecretMode() {
        session.exitSecretMode()
    }
}
