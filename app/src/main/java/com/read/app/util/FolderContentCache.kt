package com.read.app.util

import android.content.Context
import com.read.app.domain.model.Book
import com.read.app.domain.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 文件夹内容集中缓存：两个 ViewModel 共享同一个数据源，保证状态一致
// 核心原则：文件夹添加后内容很少变化，积极缓存
//
// 四张表：
//   pwdMap          : folderId -> password     密码缓存（"" = 未加密，所有文件夹都有值）
//   folderBooks     : folderId -> books        每个文件夹包含的书籍
//   passwordFolders : password -> folders      反向索引：密码 -> 绑定该密码的文件夹列表
//   normalFolders   : List<Folder>             预计算：普通模式可见的文件夹（未加密）
//   unencryptedBooks: List<Book>               预计算：全部未加密书籍（"全部书籍"视图）
object FolderContentCache {

    // 未加密文件夹的密码标记：空字符串表示没有设置密码
    const val UNENCRYPTED = ""

    private val mutex = Mutex()

    // ---- 密码缓存（所有文件夹都有值，"" = 未加密） ----
    private val _pwdMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    val pwdMap: StateFlow<Map<Long, String>> = _pwdMap.asStateFlow()

    // ---- 文件夹 -> 书籍列表 ----
    private val _folderBooks = MutableStateFlow<Map<Long, List<Book>>>(emptyMap())

    // ---- 反向索引：密码 -> 文件夹列表（隐身模式快速查找，不含未加密文件夹） ----
    private val _passwordFolders = MutableStateFlow<Map<String, List<Folder>>>(emptyMap())

    // ---- 预计算：普通模式可见的文件夹（未加密的） ----
    private val _normalFolders = MutableStateFlow<List<Folder>>(emptyList())

    // ---- 预计算：全部未加密书籍（"全部书籍"视图直接读取，无需过滤） ----
    private val _unencryptedBooks = MutableStateFlow<List<Book>>(emptyList())

    // 当前已知的文件夹列表（用于反向索引构建）
    private var knownFolders: List<Folder> = emptyList()

    // 缓存是否已初始化
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    // ========== 初始化 ==========

    // 启动时调用一次：读取所有文件夹密码，构建缓存
    suspend fun init(
        context: Context,
        folders: List<Folder>,
        allBooks: List<Book>
    ) = mutex.withLock {
        knownFolders = folders

        // 1. 读取密码（仅首次读取未缓存的），null → UNENCRYPTED
        val currentPwd = _pwdMap.value.toMutableMap()
        val toRead = folders.filter { it.id !in currentPwd }
        if (toRead.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                toRead.forEach { f ->
                    currentPwd[f.id] = FileUtil.readFolderPassword(context, f.path) ?: UNENCRYPTED
                }
            }
        }
        // 清除已删除文件夹的密码
        val validIds = folders.map { it.id }.toSet()
        val cleanPwd = currentPwd.filterKeys { it in validIds }
        _pwdMap.value = cleanPwd

        // 2. 构建 folderBooks
        rebuildFolderBooksLocked(allBooks)

        // 3. 构建反向索引 + 预计算普通模式文件夹
        rebuildPasswordFoldersLocked(folders, cleanPwd)

        _initialized.value = true
    }

    // ========== 查询接口 ==========

    // 获取某文件夹的书籍列表（缓存命中 O(1)）
    fun getBooksForFolder(folderId: Long?): List<Book> {
        return if (folderId == null) _unencryptedBooks.value
        else _folderBooks.value[folderId] ?: emptyList()
    }

    // 获取绑定某密码的文件夹列表（隐身模式用）
    fun getFoldersForPassword(password: String): List<Folder> {
        return _passwordFolders.value[password] ?: emptyList()
    }

    // 普通模式可见文件夹（预计算，直接读取）
    val normalFolders: StateFlow<List<Folder>> = _normalFolders.asStateFlow()

    // ========== 更新接口 ==========

    // 书籍或文件夹变化时重建整个缓存
    suspend fun rebuild(
        context: Context,
        folders: List<Folder>,
        allBooks: List<Book>
    ) = mutex.withLock {
        knownFolders = folders

        // 确保密码已读取
        val currentPwd = _pwdMap.value.toMutableMap()
        val toRead = folders.filter { it.id !in currentPwd }
        if (toRead.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                toRead.forEach { f ->
                    currentPwd[f.id] = FileUtil.readFolderPassword(context, f.path) ?: UNENCRYPTED
                }
            }
        }
        val validIds = folders.map { it.id }.toSet()
        val cleanPwd = currentPwd.filterKeys { it in validIds }
        _pwdMap.value = cleanPwd

        rebuildFolderBooksLocked(allBooks)
        rebuildPasswordFoldersLocked(folders, cleanPwd)
    }

    // 更新单个文件夹密码（设置/清除密码后调用）
    suspend fun updatePassword(folderId: Long, password: String?) = mutex.withLock {
        _pwdMap.value = _pwdMap.value + (folderId to (password ?: UNENCRYPTED))
        // 反向索引 + 普通模式文件夹需要重建
        rebuildPasswordFoldersLocked(knownFolders, _pwdMap.value)
        // 全部书籍也需要重建（加密状态变了）
        rebuildUnencryptedBooksLocked()
    }

    // 移除已删除的文件夹
    suspend fun removeFolder(folderId: Long) = mutex.withLock {
        _pwdMap.value = _pwdMap.value - folderId
        _folderBooks.value = _folderBooks.value - folderId
        knownFolders = knownFolders.filter { it.id != folderId }
        rebuildPasswordFoldersLocked(knownFolders, _pwdMap.value)
        rebuildUnencryptedBooksLocked()
    }

    // 清空缓存（刷新时调用）
    suspend fun clear() = mutex.withLock {
        _pwdMap.value = emptyMap()
        _folderBooks.value = emptyMap()
        _passwordFolders.value = emptyMap()
        _normalFolders.value = emptyList()
        _unencryptedBooks.value = emptyList()
        knownFolders = emptyList()
        _initialized.value = false
    }

    // ========== 内部方法（持锁调用） ==========

    // 构建 folderBooks
    private fun rebuildFolderBooksLocked(allBooks: List<Book>) {
        val newMap = mutableMapOf<Long, List<Book>>()
        knownFolders.forEach { folder ->
            newMap[folder.id] = allBooks.filter { it.folderId == folder.id }
        }
        _folderBooks.value = newMap
        rebuildUnencryptedBooksLocked()
    }

    // "全部书籍"：只包含未加密文件夹的书（预计算，UI 直接读取无需过滤）
    private fun rebuildUnencryptedBooksLocked() {
        val unencryptedIds = _pwdMap.value.filter { it.value == UNENCRYPTED }.keys
        _unencryptedBooks.value = _folderBooks.value.entries
            .filter { it.key in unencryptedIds }
            .flatMap { it.value }
    }

    // 构建反向索引 + 预计算普通模式文件夹
    private fun rebuildPasswordFoldersLocked(folders: List<Folder>, pwdMap: Map<Long, String>) {
        // 反向索引：只包含有真实密码的文件夹（跳过 UNENCRYPTED）
        val map = mutableMapOf<String, MutableList<Folder>>()
        val normal = mutableListOf<Folder>()
        folders.forEach { folder ->
            val pwd = pwdMap[folder.id] ?: UNENCRYPTED
            if (pwd == UNENCRYPTED) {
                normal.add(folder)
            } else {
                map.getOrPut(pwd) { mutableListOf() }.add(folder)
            }
        }
        _passwordFolders.value = map
        _normalFolders.value = normal
    }
}
