package com.kinny.localmusicplayer.util

/**
 * 时间格式化工具：将毫秒转换为 mm:ss 格式。
 */
fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
