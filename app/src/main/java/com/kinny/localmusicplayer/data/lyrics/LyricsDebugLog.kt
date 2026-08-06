package com.kinny.localmusicplayer.data.lyrics

import android.util.Log

/**
 * Description: 歌词加载调试日志工具
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
internal object LyricsDebugLog {
    private const val TAG = "KinnyLyrics"

    fun d(step: String, detail: () -> String = { "" }) {
        val message = detail()
        if (message.isEmpty()) {
            Log.d(TAG, step)
        } else {
            Log.d(TAG, "$step | $message")
        }
    }

    fun w(step: String, detail: () -> String = { "" }) {
        val message = detail()
        if (message.isEmpty()) {
            Log.w(TAG, step)
        } else {
            Log.w(TAG, "$step | $message")
        }
    }

    fun e(step: String, throwable: Throwable? = null, detail: () -> String = { "" }) {
        val message = detail()
        if (throwable != null) {
            Log.e(TAG, if (message.isEmpty()) step else "$step | $message", throwable)
        } else if (message.isEmpty()) {
            Log.e(TAG, step)
        } else {
            Log.e(TAG, "$step | $message")
        }
    }

    /** 截断长文本，避免 Logcat 单行过长 */
    fun preview(text: String, maxLen: Int = 120): String {
        val singleLine = text.replace('\n', ' ').replace('\r', ' ')
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "…"
    }
}
