package com.kinny.localmusicplayer.ui.splash

import android.content.Context
import android.media.MediaPlayer
import com.kinny.localmusicplayer.R

/**
 * Description: 启动页欢迎语音播放器，播放 raw 目录下的 MP3 资源
 * Author: kinny
 * Created: 2026/8/5 16:21
 */
class SplashGreetingPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var hasPlayed = false

    /** 播放启动欢迎语音（仅播放一次） */
    fun playWelcome() {
        if (hasPlayed) return
        hasPlayed = true
        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(appContext, R.raw.splash_greeting)?.apply {
                setOnCompletionListener {
                    releasePlayer()
                }
                start()
            }
        }
    }

    fun stop() {
        mediaPlayer?.run {
            if (isPlaying) stop()
        }
    }

    fun shutdown() {
        releasePlayer()
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
