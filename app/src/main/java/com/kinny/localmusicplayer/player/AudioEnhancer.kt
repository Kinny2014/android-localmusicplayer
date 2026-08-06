package com.kinny.localmusicplayer.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Description: 音频增强器，应用系统 EQ 与响度增强
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@OptIn(UnstableApi::class)
class AudioEnhancer {

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedSessionId: Int = AUDIO_SESSION_ID_UNSET

    /** 绑定到 ExoPlayer，监听 audioSessionId 变化 */
    fun attachTo(player: ExoPlayer) {
        player.addListener(
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    setupEffects(audioSessionId)
                }
            },
        )
        // 不在此处读取 sessionId：Media3 1.6+ 创建播放器时尚未分配
    }

    /** 释放音效资源 */
    fun release() {
        equalizer?.release()
        loudnessEnhancer?.release()
        equalizer = null
        loudnessEnhancer = null
        attachedSessionId = AUDIO_SESSION_ID_UNSET
    }

    private fun setupEffects(audioSessionId: Int) {
        if (audioSessionId == AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == attachedSessionId) return

        // 先释放旧实例，再绑定新 session（切歌时 session 可能变化）
        equalizer?.release()
        loudnessEnhancer?.release()
        equalizer = null
        loudnessEnhancer = null
        attachedSessionId = audioSessionId

        runCatching {
            equalizer = Equalizer(/* priority= */ 0, audioSessionId).apply {
                enabled = true
                val musicPreset = findMusicPresetIndex()
                if (musicPreset >= 0) {
                    usePreset(musicPreset.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "Equalizer 不可用: ${it.message}") }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(LOUDNESS_GAIN_MB)
                enabled = true
            }
        }.onFailure { Log.w(TAG, "LoudnessEnhancer 不可用: ${it.message}") }
    }

    /** 查找名称含 music/音乐 的系统 EQ 预设 */
    private fun Equalizer.findMusicPresetIndex(): Int {
        for (i in 0 until numberOfPresets) {
            val name = getPresetName(i.toShort()).lowercase()
            if ("music" in name || "音乐" in name || "normal" in name) return i
        }
        return -1
    }

    companion object {
        private const val TAG = "AudioEnhancer"
        /** 未分配音频会话 ID（与 Media3 / AudioManager 约定一致，值为 0） */
        private const val AUDIO_SESSION_ID_UNSET = 0
        /** 响度增强目标（毫贝），约 +3 dB */
        private const val LOUDNESS_GAIN_MB = 300
    }
}
