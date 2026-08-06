package com.kinny.localmusicplayer.data.lyrics

/**
 * 单行 LRC 歌词数据模型
 */
data class LrcLine(
    val timestampMs: Long,
    /** 主歌词（英文歌曲时为英文行） */
    val text: String,
    /** 翻译文本（如中文），不参与同步高亮 */
    val translation: String? = null,
)

/**
 * 歌词加载结果数据模型，含同步查表方法
 */
data class Lyrics(
    val lines: List<LrcLine>,
    val source: LyricsSource,
) {
    val isSynced: Boolean = lines.any { it.timestampMs >= 0 }

    /** 根据当前播放位置获取应显示的歌词行索引 */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (!isSynced) return 0
        var result = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= positionMs) {
                result = i
            } else {
                break
            }
        }
        return result
    }

    fun lineAt(positionMs: Long): String? {
        val index = indexAt(positionMs)
        return index.takeIf { it >= 0 }?.let { lines[it].text }
    }
}

/**
 * 歌词来源枚举
 */
enum class LyricsSource {
    /** 同目录 .lrc 文件 */
    LRC_FILE,
    /** MP3 ID3 USLT 内嵌歌词 */
    ID3_EMBEDDED,
    /** 未找到歌词 */
    NONE,
}
