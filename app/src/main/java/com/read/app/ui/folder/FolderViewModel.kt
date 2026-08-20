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
import com.read.app.util.FolderContentCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

    // 密码从共享缓存读取（与 LibraryViewModel 保持一致）
    val folderPasswords: StateFlow<Map<Long, String?>> = FolderContentCache.pwdMap

    init {
        // 初始化缓存：读取密码 + 构建文件夹书籍映射
        viewModelScope.launch {
            val folders = folderRepository.getAllFolders().first()
            val allBooks = bookRepository.getAllBooks().first()
            FolderContentCache.init(context, folders, allBooks)
        }
        // 书籍变化时重建缓存
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { allBooks ->
                val folders = folderRepository.getAllFolders().first()
                FolderContentCache.rebuild(context, folders, allBooks)
            }
        }
        // 文件夹变化时重建缓存（新增/删除文件夹）
        viewModelScope.launch {
            folderRepository.getAllFolders().collect { folders ->
                val allBooks = bookRepository.getAllBooks().first()
                FolderContentCache.rebuild(context, folders, allBooks)
            }
        }
    }

    // 普通模式只显示未设密码的文件夹；隐身模式只显示绑定当前密码的
    val folders: StateFlow<List<Folder>> = combine(
        folderRepository.getAllFolders(),
        session.secretPassword,
        FolderContentCache.pwdMap
    ) { all, pwd, pwdMap ->
        if (pwd == null) all.filter { pwdMap[it.id] == FolderContentCache.UNENCRYPTED }
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
            FolderContentCache.removeFolder(folder.id)
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
                FolderContentCache.updatePassword(folder.id, password.ifBlank { null })
                // 密码变化后重建书籍缓存（"全部书籍"过滤会受影响）
                val folders = folderRepository.getAllFolders().first()
                val allBooks = bookRepository.getAllBooks().first()
                FolderContentCache.rebuild(context, folders, allBooks)
            }
        }
    }

    // 退出隐身模式，回到普通列表
    fun exitSecretMode() {
        session.exitSecretMode()
    }
}
