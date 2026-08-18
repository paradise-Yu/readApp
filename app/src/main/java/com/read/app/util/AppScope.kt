package com.read.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// 应用级协程作用域：生命周期与进程一致，不随 ViewModel 销毁而取消。
// 用于退出阅读器时的进度落库等"必须完成"的写操作。
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
