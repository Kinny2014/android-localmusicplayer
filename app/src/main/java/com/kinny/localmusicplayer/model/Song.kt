package com.kinny.localmusicplayer.model

import kotlinx.serialization.Serializable

/**
 * Description: 本地音乐歌曲数据模型
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val uri: String,
    /** 目录导入时匹配到的同目录 .lrc Uri（可为空） */
    val lrcUri: String? = null,
    /** 导入时用户授权的 SAF 目录树 Uri，用于运行时查找兄弟文件 */
    val sourceTreeUri: String? = null,
)
