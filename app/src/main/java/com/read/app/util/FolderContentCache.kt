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
// 三张表：
//   pwdMap         : folderId -> password     密码缓存（folderId 不在 map 中 = 未读取）
//   folderBooks    : folderId -> books        每个文件夹包含的书籍
//   passwordFolders: password -> folders      反向索引：密码 -> 绑定该密码的文件夹列表
object FolderContentCache {

    private val mutex = Mutex()

    // ---- 密码缓存 ----
    private val _pwdMap = MutableStateFlow<Map<Long, String?>>(emptyMap())
    val pwdMap: StateFlow<Map<Long, String?>> = _pwdMap.asStateFlow()

    // ---- 文件夹 -> 书籍列表 ----
    private val _folderBooks = MutableStateFlow<Map<Long, List<Book>>>(emptyMap())

    // ---- 反向索引：密码 -> 文件夹列表（隐身模式快速查找） ----
    private val _passwordFolders = MutableStateFlow<Map<String, List<Folder>>>(emptyMap())

    // "全部书籍"缓存 key = null
    private val _allBooksCache = MutableStateFlow<List<Book>>(emptyList())

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

        // 1. 读取密码（仅首次读取未缓存的）
        val currentPwd = _pwdMap.value.toMutableMap()
        val toRead = folders.filter { it.id !in currentPwd }
        if (toRead.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                toRead.forEach { f ->
                    currentPwd[f.id] = FileUtil.readFolderPassword(context, f.path)
                }
            }
        }
        // 清除已删除文件夹的密码
        val validIds = folders.map { it.id }.toSet()
        val cleanPwd = currentPwd.filterKeys { it in validIds }
        _pwdMap.value = cleanPwd

        // 2. 构建 folderBooks
        rebuildFolderBooksLocked(allBooks)

        // 3. 构建反向索引
        rebuildPasswordFoldersLocked(folders, cleanPwd)

        _initialized.value = true
    }

    // ========== 查询接口 ==========

    // 获取某文件夹的书籍列表（缓存命中 O(1)）
    fun getBooksForFolder(folderId: Long?): List<Book> {
        return if (folderId == null) _allBooksCache.value
        else _folderBooks.value[folderId] ?: emptyList()
    }

    // 获取绑定某密码的文件夹列表（隐身模式用）
    fun getFoldersForPassword(password: String): List<Folder> {
        return _passwordFolders.value[password] ?: emptyList()
    }

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
                    currentPwd[f.id] = FileUtil.readFolderPassword(context, f.path)
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
        _pwdMap.value = _pwdMap.value + (folderId to password)
        // 反向索引需要重建
        rebuildPasswordFoldersLocked(knownFolders, _pwdMap.value)
    }

    // 移除已删除的文件夹
    suspend fun removeFolder(folderId: Long) = mutex.withLock {
        _pwdMap.value = _pwdMap.value - folderId
        _folderBooks.value = _folderBooks.value - folderId
        knownFolders = knownFolders.filter { it.id != folderId }
        rebuildPasswordFoldersLocked(knownFolders, _pwdMap.value)
        // allBooks 也需要更新
        rebuildAllBooksLocked()
    }

    // 清空缓存（刷新时调用）
    suspend fun clear() = mutex.withLock {
        _pwdMap.value = emptyMap()
        _folderBooks.value = emptyMap()
        _passwordFolders.value = emptyMap()
        _allBooksCache.value = emptyList()
        knownFolders = emptyList()
        _initialized.value = false
    }

    // ========== 内部方法（持锁调用） ==========

    // 构建 folderBooks + allBooks
    private fun rebuildFolderBooksLocked(allBooks: List<Book>) {
        val newMap = mutableMapOf<Long, List<Book>>()
        knownFolders.forEach { folder ->
            newMap[folder.id] = allBooks.filter { it.folderId == folder.id }
        }
        _folderBooks.value = newMap
        rebuildAllBooksLocked()
    }

    // "全部书籍"：排除加密文件夹 + 孤立书
    private fun rebuildAllBooksLocked() {
        val hiddenIds = _pwdMap.value.filter { it.value != null }.keys
        val validIds = knownFolders.map { it.id }.toSet()
        _allBooksCache.value = _folderBooks.value.values.flatten().filter { b ->
            b.folderId != null && b.folderId !in hiddenIds && b.folderId in validIds
        }
    }

    // 构建反向索引：password -> folders
    private fun rebuildPasswordFoldersLocked(folders: List<Folder>, pwdMap: Map<Long, String?>) {
        val map = mutableMapOf<String, MutableList<Folder>>()
        folders.forEach { folder ->
            val pwd = pwdMap[folder.id]
            if (pwd != null) {
                map.getOrPut(pwd) { mutableListOf() }.add(folder)
            }
        }
        _passwordFolders.value = map
    }
}
