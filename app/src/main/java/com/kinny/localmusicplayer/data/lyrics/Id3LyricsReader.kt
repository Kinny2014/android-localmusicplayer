package com.kinny.localmusicplayer.data.lyrics

import java.nio.charset.Charset

/**
 * Description: MP3 ID3v2 USLT 内嵌歌词读取器
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object Id3LyricsReader {

    /**
     * 读取 MP3 内嵌歌词（USLT 帧）。
     *
     * @return 歌词纯文本，未找到则 null
     */
    fun readUsltLyrics(mp3Bytes: ByteArray): String? {
        if (mp3Bytes.size < 10) return null

        // ID3v2 头部："ID3" + version(2) + flags(1) + size(4 syncsafe)
        if (mp3Bytes[0] != 'I'.code.toByte() ||
            mp3Bytes[1] != 'D'.code.toByte() ||
            mp3Bytes[2] != '3'.code.toByte()
        ) {
            return null
        }

        val tagSize = syncsafeSize(
            mp3Bytes[6], mp3Bytes[7], mp3Bytes[8], mp3Bytes[9],
        )
        val tagEnd = (10 + tagSize).coerceAtMost(mp3Bytes.size)
        var offset = 10

        while (offset + 10 <= tagEnd) {
            val frameId = String(mp3Bytes, offset, 4, Charsets.US_ASCII)
            if (!frameId.matches(Regex("[A-Z0-9]{4}"))) break

            val frameSize = readFrameSize(mp3Bytes, offset + 4, mp3Bytes[5].toInt())
            val frameHeaderSize = if (mp3Bytes[5].toInt() and 0x02 != 0) 10 else 8
            val frameDataStart = offset + frameHeaderSize
            val frameDataEnd = (frameDataStart + frameSize).coerceAtMost(tagEnd)

            if (frameId == "USLT" && frameDataEnd > frameDataStart) {
                return parseUsltFrame(mp3Bytes, frameDataStart, frameDataEnd)
            }

            offset = frameDataEnd
        }
        return null
    }

    /** 解析 USLT 帧：encoding(1) + language(3) + descriptor + \0 + lyrics */
    private fun parseUsltFrame(bytes: ByteArray, start: Int, end: Int): String? {
        if (start >= end) return null
        val encoding = bytes[start].toInt()
        var pos = start + 4 // skip encoding + language

        // skip content descriptor
        pos = skipNullTerminated(bytes, pos, end, encoding)
        if (pos >= end) return null

        val lyricsBytes = bytes.copyOfRange(pos, end)
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charset.forName("UTF-16BE")
            3 -> Charset.forName("UTF-8")
            else -> Charsets.ISO_8859_1
        }
        return lyricsBytes.toString(charset).trim().takeIf { it.isNotBlank() }
    }

    private fun skipNullTerminated(bytes: ByteArray, start: Int, end: Int, encoding: Int): Int {
        var pos = start
        if (encoding == 1 || encoding == 2) {
            while (pos + 1 < end) {
                if (bytes[pos] == 0.toByte() && bytes[pos + 1] == 0.toByte()) return pos + 2
                pos += 2
            }
        } else {
            while (pos < end) {
                if (bytes[pos] == 0.toByte()) return pos + 1
                pos++
            }
        }
        return end
    }

    /** ID3v2.4 使用 syncsafe size；v2.3 使用普通 int */
    private fun readFrameSize(bytes: ByteArray, offset: Int, versionMinor: Int): Int {
        return if (versionMinor >= 4) {
            syncsafeSize(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3])
        } else {
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }
    }

    private fun syncsafeSize(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int =
        (b0.toInt() and 0x7F shl 21) or
            (b1.toInt() and 0x7F shl 14) or
            (b2.toInt() and 0x7F shl 7) or
            (b3.toInt() and 0x7F)
}
