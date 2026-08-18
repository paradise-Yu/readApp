package com.read.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val addedTime: Long = System.currentTimeMillis(),
    // 访问密码（明文）：非空时文件夹在普通模式下隐藏，输入对应密码才能进入
    val password: String? = null
)
