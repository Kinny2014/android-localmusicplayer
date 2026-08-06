package com.kinny.localmusicplayer.model

import androidx.compose.runtime.Immutable
import com.kinny.localmusicplayer.data.lyrics.Lyrics
import com.kinny.localmusicplayer.data.lyrics.LyricsSource

/**
 * Description: 播放列表页面 UI 状态数据模型
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Immutable
data class PlaylistUiState(
    val songs: List<Song> = emptyList(),
    val currentSongId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = true,
    val hasStoragePermission: Boolean = false,
    /** 当前歌曲歌词 */
    val currentLyrics: Lyrics = Lyrics(emptyList(), LyricsSource.NONE),
    /** 当前应显示的歌词行（已按播放进度匹配） */
    val currentLyricLine: String? = null,
    /** 当前歌词行索引，用于全屏页高亮与自动滚动 */
    val currentLyricIndex: Int = -1,
)

/**
 * 播放列表一次性 UI 事件密封接口
 */
sealed interface PlaylistUiEvent {
    /**
     * Snackbar 消息展示事件
     */
    data class ShowMessage(val message: String) : PlaylistUiEvent
}
