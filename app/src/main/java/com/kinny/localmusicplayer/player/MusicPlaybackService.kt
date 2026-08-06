package com.kinny.localmusicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kinny.localmusicplayer.R
import com.kinny.localmusicplayer.model.Song

/**
 * Description: Media3 后台音乐播放服务
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val audioEnhancer = AudioEnhancer()
    /** 连续播放失败次数，用于避免全部曲目不可播时无限跳歌 */
    private var consecutivePlayErrors = 0

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 加大缓冲区：本地文件sequential读取时更流畅
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ 50_000,
                /* maxBufferMs= */ 120_000,
                /* bufferForPlaybackMs= */ 2_500,
                /* bufferForPlaybackAfterRebufferMs= */ 5_000,
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                // 保留曲目间静音间隙，不跳过
                skipSilenceEnabled = false
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        skipToNextAfterError(this@apply)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && isPlaying) {
                            consecutivePlayErrors = 0
                        }
                    }
                })
            }

        audioEnhancer.attachTo(player)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioEnhancer.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 当前曲目无法播放时自动切到下一首；列表循环模式下最后一首会回到第一首 */
    private fun skipToNextAfterError(player: Player) {
        if (player.mediaItemCount == 0) return

        consecutivePlayErrors++
        if (consecutivePlayErrors >= player.mediaItemCount) {
            player.pause()
            consecutivePlayErrors = 0
            return
        }

        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(0, 0L)
        }
        player.prepare()
        player.playWhenReady = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * 播放服务伴生对象，提供MediaItem构建工具
     */
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "music_playback_channel"

        fun buildMediaItems(songs: List<Song>): List<MediaItem> = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist ?: "")
                        .build(),
                )
                .build()
        }
    }
}
