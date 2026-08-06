package com.kinny.localmusicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.kinny.localmusicplayer.di.IoDispatcher
import com.kinny.localmusicplayer.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Description: 播放器连接管理器，负责 UI 与 MediaController 通信
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Singleton
class MusicPlayerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, MusicPlaybackService::class.java),
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** 连接到后台播放服务 */
    suspend fun connect() = withContext(ioDispatcher) {
        if (mediaController != null) return@withContext
        val controller = buildController()
        mediaController = controller
        _isConnected.value = true
    }

    /** 断开连接并释放资源 */
    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        _isConnected.value = false
    }

    /** 更新播放列表并可选地从指定索引与进度开始播放 */
    suspend fun setPlaylist(
        songs: List<Song>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = false,
    ) {
        val controller = requireController()
        val items = MusicPlaybackService.buildMediaItems(songs)
        if (items.isEmpty()) {
            controller.stop()
            controller.clearMediaItems()
            return
        }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        controller.setMediaItems(items, safeIndex, startPositionMs.coerceAtLeast(0L))
        controller.prepare()
        controller.playWhenReady = autoPlay
    }

    /** 播放指定索引的歌曲；若已在该索引且正在播放，则保持当前进度。 */
    fun playAt(index: Int) {
        val controller = mediaController ?: return
        if (index !in 0 until controller.mediaItemCount) return

        if (controller.currentMediaItemIndex == index) {
            if (!controller.isPlaying) {
                controller.play()
            }
            return
        }

        controller.seekTo(index, 0L)
        controller.play()
    }

    /** 播放 / 暂停切换 */
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    /** 跳转到指定播放位置（毫秒） */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** 下一首 */
    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    /** 上一首 */
    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    /** 观察播放器状态变化 */
    fun observePlayerState(): Flow<PlayerState> = callbackFlow {
        val controller = mediaController
        if (controller == null) {
            trySend(PlayerState())
            close()
            return@callbackFlow
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                trySend(player.toPlayerState())
            }
        }

        controller.addListener(listener)
        trySend(controller.toPlayerState())

        awaitClose {
            controller.removeListener(listener)
        }
    }.distinctUntilChanged()

    /** 获取当前播放索引，若无则返回 -1 */
    fun currentMediaItemIndex(): Int =
        mediaController?.currentMediaItemIndex ?: -1

    /** 获取当前播放器状态快照 */
    fun currentState(): PlayerState = mediaController?.toPlayerState() ?: PlayerState()

    private suspend fun buildController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation {
                MediaController.releaseFuture(future)
            }
        }

    private fun requireController(): MediaController =
        checkNotNull(mediaController) { "MediaController 尚未连接，请先调用 connect()" }

    /**
     * 播放器快照状态数据模型
     */
    data class PlayerState(
        val currentMediaId: String? = null,
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val currentIndex: Int = -1,
    )

    private fun Player.toPlayerState(): PlayerState = PlayerState(
        currentMediaId = currentMediaItem?.mediaId,
        isPlaying = isPlaying,
        currentPositionMs = currentPosition,
        durationMs = duration.coerceAtLeast(0L),
        currentIndex = currentMediaItemIndex,
    )
}
