package com.kinny.localmusicplayer.ui.playlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kinny.localmusicplayer.data.PlaylistRepository
import com.kinny.localmusicplayer.data.UriPermissionHelper
import com.kinny.localmusicplayer.data.lyrics.LyricsLoader
import com.kinny.localmusicplayer.data.lyrics.LyricsDebugLog
import com.kinny.localmusicplayer.data.lyrics.LyricsSource
import com.kinny.localmusicplayer.data.lyrics.SafDocumentHelper
import com.kinny.localmusicplayer.model.PlaylistUiEvent
import com.kinny.localmusicplayer.model.PlaylistUiState
import com.kinny.localmusicplayer.model.Song
import com.kinny.localmusicplayer.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

/**
 * Description: 播放列表 ViewModel，协调 UI、仓库与播放器数据流
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val application: Application,
    private val playlistRepository: PlaylistRepository,
    private val playerManager: MusicPlayerManager,
    private val lyricsLoader: LyricsLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistUiEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PlaylistUiEvent> = _events.asSharedFlow()

    private var progressJob: Job? = null
    private var lyricsJob: Job? = null
    private var lastSyncedSongIds: List<String> = emptyList()
    private var lastLyricsSongId: String? = null

    init {
        viewModelScope.launch {
            playerManager.connect()
            observePlaylist()
            observePlayerState()
            startProgressTicker()
        }
    }

    fun updatePermissionState(hasPermission: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    /** 导入多个音频文件（SAF，无需存储权限） */
    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val resolver = application.contentResolver
            val result = playlistRepository.importFiles(uris) { uri ->
                UriPermissionHelper.persistFileReadPermission(resolver, uri)
            }
            handleImportResult(result)
        }
    }

    /** 导入文件夹（SAF 目录树，无需存储权限） */
    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            val resolver = application.contentResolver
            val result = playlistRepository.importFolder(treeUri) { uri ->
                UriPermissionHelper.persistTreeReadPermission(resolver, uri)
            }
            handleImportResult(result)
        }
    }

    /**
     * 播放指定歌曲；若已是当前正在播放的歌曲，则不重置进度（仅用于打开详情页等场景）。
     */
    fun playSong(song: Song) {
        val index = _uiState.value.songs.indexOfFirst { it.id == song.id }
        if (index < 0) return

        if (song.id == _uiState.value.currentSongId) {
            return
        }

        playerManager.playAt(index)
        loadLyricsForSong(song.id)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        updateLyricLine(positionMs)
    }

    fun removeSong(songId: String) {
        viewModelScope.launch {
            val currentId = _uiState.value.currentSongId
            val removingCurrent = currentId == songId
            playlistRepository.removeSong(songId)
            if (removingCurrent) {
                playerManager.pause()
            }
            // 非当前歌曲的删除由 observePlaylist → syncPlaylistToPlayer 处理，保留播放进度
        }
    }

    /**
     * 拖拽重排歌曲：乐观更新 UI，持久化顺序并同步播放器列表（不打断当前播放）。
     */
    fun moveSong(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.songs
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        if (fromIndex == toIndex) return

        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        _uiState.update { it.copy(songs = reordered) }
        lastSyncedSongIds = reordered.map { it.id }

        viewModelScope.launch {
            playlistRepository.savePlaylistOrder(reordered)
            syncPlaylistToPlayer(reordered, preservePlayback = true)
        }
    }

    private fun handleImportResult(result: Result<Int>) {
        result.fold(
            onSuccess = { count ->
                if (count > 0) {
                    _events.tryEmit(PlaylistUiEvent.ShowMessage("已导入 $count 首歌曲"))
                } else {
                    _events.tryEmit(PlaylistUiEvent.ShowMessage("所选目录中未发现 MP3 文件"))
                }
            },
            onFailure = { error ->
                val message = error.message?.let { "导入失败：$it" } ?: "导入失败，请重试"
                _events.tryEmit(PlaylistUiEvent.ShowMessage(message))
            },
        )
    }

    private fun observePlaylist() {
        viewModelScope.launch {
            playlistRepository.observePlaylist().collect { songs ->
                val previousSong = _uiState.value.currentSongId?.let { id ->
                    _uiState.value.songs.find { it.id == id }
                }
                _uiState.update { it.copy(songs = songs, isLoading = false) }
                val newIds = songs.map { it.id }
                if (newIds != lastSyncedSongIds) {
                    syncPlaylistToPlayer(songs, preservePlayback = true)
                    lastSyncedSongIds = newIds
                }
                // 重新导入后 lrcUri 可能更新，需重新加载歌词
                val currentId = _uiState.value.currentSongId ?: return@collect
                val updatedSong = songs.find { it.id == currentId } ?: return@collect
                if (previousSong?.lrcUri != updatedSong.lrcUri ||
                    previousSong?.sourceTreeUri != updatedSong.sourceTreeUri
                ) {
                    lastLyricsSongId = null
                    loadLyricsForSong(currentId)
                }
            }
        }
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            playerManager.observePlayerState().collect { state ->
                val songChanged = state.currentMediaId != _uiState.value.currentSongId
                _uiState.update {
                    val lyrics = it.currentLyrics
                    val index = lyrics.indexAt(state.currentPositionMs)
                    it.copy(
                        currentSongId = state.currentMediaId,
                        isPlaying = state.isPlaying,
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        currentLyricLine = lyrics.lineAt(state.currentPositionMs),
                        currentLyricIndex = index,
                    )
                }
                if (songChanged && state.currentMediaId != null) {
                    loadLyricsForSong(state.currentMediaId)
                }
            }
        }
    }

    /** 异步加载当前歌曲歌词 */
    private fun loadLyricsForSong(songId: String) {
        if (songId == lastLyricsSongId && _uiState.value.currentLyrics.lines.isNotEmpty()) return
        lastLyricsSongId = songId
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val song = _uiState.value.songs.find { it.id == songId } ?: run {
                LyricsDebugLog.w("PlaylistViewModel.loadLyricsForSong") { "song not found: $songId" }
                return@launch
            }
            LyricsDebugLog.d("PlaylistViewModel.loadLyricsForSong.start") {
                "title=${song.title}, uri=${song.uri}, lrcUri=${song.lrcUri}, sourceTreeUri=${song.sourceTreeUri}"
            }
            val effectiveTreeUri = song.sourceTreeUri?.let(Uri::parse)
                ?: SafDocumentHelper.resolveTreeUri(song.uri.toUri())
            val lyrics = lyricsLoader.loadLyrics(
                songUri = song.uri.toUri(),
                lrcUri = song.lrcUri?.let(Uri::parse),
                sourceTreeUri = effectiveTreeUri,
                title = song.title,
            )
            // 防止快速切歌时歌词错位
            if (_uiState.value.currentSongId != songId) {
                LyricsDebugLog.d("PlaylistViewModel.loadLyricsForSong") { "song changed during load, discard" }
                return@launch
            }
            LyricsDebugLog.d("PlaylistViewModel.loadLyricsForSong.done") {
                "source=${lyrics.source}, lines=${lyrics.lines.size}, index=${lyrics.indexAt(_uiState.value.currentPositionMs)}"
            }
            _uiState.update {
                it.copy(
                    currentLyrics = lyrics,
                    currentLyricLine = lyrics.lineAt(it.currentPositionMs),
                    currentLyricIndex = lyrics.indexAt(it.currentPositionMs),
                )
            }
        }
    }

    /** 根据播放进度刷新当前歌词行 */
    private fun updateLyricLine(positionMs: Long) {
        val lyrics = _uiState.value.currentLyrics
        if (lyrics.source == LyricsSource.NONE) return
        val index = lyrics.indexAt(positionMs)
        val line = lyrics.lineAt(positionMs)
        if (index != _uiState.value.currentLyricIndex || line != _uiState.value.currentLyricLine) {
            _uiState.update {
                it.copy(currentLyricIndex = index, currentLyricLine = line)
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_UPDATE_INTERVAL_MS.milliseconds)
                if (_uiState.value.isPlaying) {
                    val state = playerManager.currentState()
                    _uiState.update {
                        it.copy(
                            currentPositionMs = state.currentPositionMs,
                            durationMs = state.durationMs,
                        )
                    }
                    updateLyricLine(state.currentPositionMs)
                }
            }
        }
    }

    /**
     * 将 UI 播放列表同步到 Media3 播放器。
     *
     * @param preservePlayback 为 true 且当前歌曲仍在新列表中时，保留播放进度与播放/暂停状态，
     *   用于删除非当前歌曲或拖拽重排；仅影响「下一首」顺序，不打断当前播放。
     */
    private suspend fun syncPlaylistToPlayer(
        songs: List<Song>,
        preservePlayback: Boolean = false,
    ) {
        if (songs.isEmpty()) {
            playerManager.setPlaylist(emptyList())
            return
        }

        val currentId = _uiState.value.currentSongId
        val currentStillInList = currentId != null && songs.any { it.id == currentId }

        if (preservePlayback && currentStillInList) {
            val startIndex = songs.indexOfFirst { it.id == currentId }
            playerManager.setPlaylist(
                songs = songs,
                startIndex = startIndex,
                startPositionMs = playerManager.currentState().currentPositionMs,
                autoPlay = _uiState.value.isPlaying,
            )
            return
        }

        val startIndex = if (currentStillInList) {
            songs.indexOfFirst { it.id == currentId }
        } else {
            0
        }
        playerManager.setPlaylist(
            songs = songs,
            startIndex = startIndex,
            autoPlay = currentStillInList && _uiState.value.isPlaying,
        )
    }

    override fun onCleared() {
        progressJob?.cancel()
        lyricsJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}
