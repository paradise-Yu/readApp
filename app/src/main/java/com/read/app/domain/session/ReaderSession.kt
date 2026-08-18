package com.read.app.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// 全局会话状态：书架与文件夹页共享"隐身模式"（当前输入的文件夹访问密码）
@Singleton
class ReaderSession @Inject constructor() {

    private val _secretPassword = MutableStateFlow<String?>(null)

    // 非空表示处于隐身模式，只显示绑定该密码的文件夹
    val secretPassword: StateFlow<String?> = _secretPassword.asStateFlow()

    fun enterSecretMode(password: String) {
        _secretPassword.value = password
    }

    fun exitSecretMode() {
        _secretPassword.value = null
    }
}
