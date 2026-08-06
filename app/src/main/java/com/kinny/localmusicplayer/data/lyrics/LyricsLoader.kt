package com.kinny.localmusicplayer.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kinny.localmusicplayer.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/** ID3 标签通常位于 MP3 文件头部，256 KB 足够覆盖绝大多数标签 */
private const val ID3_READ_BYTES = 256 * 1024

/**
 * Description: 歌词加载器，按优先级从 LRC 文件或 ID3 内嵌加载歌词
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Singleton
class LyricsLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun loadLyrics(
        songUri: Uri,
        lrcUri: Uri? = null,
        sourceTreeUri: Uri? = null,
        title: String? = null,
    ): Lyrics = withContext(ioDispatcher) {
        LyricsDebugLog.d("LyricsLoader.loadLyrics.start") {
            "songUri=$songUri, knownLrcUri=$lrcUri, sourceTreeUri=$sourceTreeUri, title=$title"
        }

        loadFromLrcFile(songUri, lrcUri, sourceTreeUri, title)?.also { lyrics ->
            LyricsDebugLog.d("LyricsLoader.loadLyrics.success") {
                "source=${lyrics.source}, lines=${lyrics.lines.size}, synced=${lyrics.isSynced}"
            }
            return@withContext lyrics
        }

        LyricsDebugLog.d("LyricsLoader.loadLyrics") { "LRC not loaded, trying ID3 embedded" }
        loadFromId3Embedded(songUri)?.also { lyrics ->
            LyricsDebugLog.d("LyricsLoader.loadLyrics.success") {
                "source=${lyrics.source}, lines=${lyrics.lines.size}"
            }
            return@withContext lyrics
        }

        LyricsDebugLog.w("LyricsLoader.loadLyrics.failed") { "no lyrics found for songUri=$songUri" }
        Lyrics(emptyList(), LyricsSource.NONE)
    }

    /** 查找与 MP3 同目录、同主文件名的 .lrc 文件 */
    private fun loadFromLrcFile(
        songUri: Uri,
        knownLrcUri: Uri?,
        sourceTreeUri: Uri?,
        title: String?,
    ): Lyrics? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, songUri)
        val baseName = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

        LyricsDebugLog.d("LyricsLoader.loadFromLrcFile") {
            "displayName=$displayName, baseName=$baseName, knownLrcUri=$knownLrcUri"
        }

        val resolvedLrcUri = knownLrcUri ?: run {
            val namesToTry = SafDocumentHelper.lrcNameCandidates(baseName, title)
            LyricsDebugLog.d("LyricsLoader.loadFromLrcFile.search") {
                "namesToTry=$namesToTry"
            }
            namesToTry.firstNotNullOfOrNull { name ->
                SafSiblingFinder.findLrcUri(
                    context = context,
                    songUri = songUri,
                    baseName = name,
                    sourceTreeUri = sourceTreeUri ?: SafDocumentHelper.resolveTreeUri(songUri),
                )?.also { found ->
                    LyricsDebugLog.d("LyricsLoader.loadFromLrcFile.found") {
                        "baseName=$name, lrcUri=$found"
                    }
                }
            }
        }

        if (resolvedLrcUri == null) {
            LyricsDebugLog.w("LyricsLoader.loadFromLrcFile") { "LRC uri not resolved" }
            return null
        }

        val content = readLrcText(resolver, resolvedLrcUri)
        if (content == null) {
            LyricsDebugLog.w("LyricsLoader.loadFromLrcFile") {
                "failed to read lrc content, lrcUri=$resolvedLrcUri"
            }
            return null
        }

        LyricsDebugLog.d("LyricsLoader.loadFromLrcFile.content") {
            "bytes=${content.length}, preview=${LyricsDebugLog.preview(content)}"
        }

        val lines = LrcParser.parse(content)
        if (lines.isEmpty()) {
            LyricsDebugLog.w("LyricsLoader.loadFromLrcFile") {
                "parse returned 0 lines, lrcUri=$resolvedLrcUri"
            }
            return null
        }
        return Lyrics(lines = lines, source = LyricsSource.LRC_FILE)
    }

    /** 读取 LRC 文本，优先 UTF-8，解析失败时尝试 GBK（国内歌词常见编码） */
    private fun readLrcText(resolver: android.content.ContentResolver, lrcUri: Uri): String? {
        val bytes = try {
            resolver.openInputStream(lrcUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            LyricsDebugLog.e("LyricsLoader.readLrcText.openFailed", e) { "lrcUri=$lrcUri" }
            null
        } ?: run {
            LyricsDebugLog.w("LyricsLoader.readLrcText") { "openInputStream returned null, lrcUri=$lrcUri" }
            return null
        }

        if (bytes.isEmpty()) {
            LyricsDebugLog.w("LyricsLoader.readLrcText") { "empty file, lrcUri=$lrcUri" }
            return null
        }

        LyricsDebugLog.d("LyricsLoader.readLrcText") { "rawBytes=${bytes.size}, lrcUri=$lrcUri" }

        val utf8 = bytes.toString(Charsets.UTF_8)
        if (LrcParser.parse(utf8).isNotEmpty()) {
            LyricsDebugLog.d("LyricsLoader.readLrcText") { "encoding=UTF-8" }
            return utf8
        }

        val gbk = runCatching { bytes.toString(Charset.forName("GBK")) }.getOrNull()
        if (gbk != null && LrcParser.parse(gbk).isNotEmpty()) {
            LyricsDebugLog.d("LyricsLoader.readLrcText") { "encoding=GBK" }
            return gbk
        }

        val gb18030 = runCatching { bytes.toString(Charset.forName("GB18030")) }.getOrNull()
        if (gb18030 != null && LrcParser.parse(gb18030).isNotEmpty()) {
            LyricsDebugLog.d("LyricsLoader.readLrcText") { "encoding=GB18030" }
            return gb18030
        }

        LyricsDebugLog.w("LyricsLoader.readLrcText") {
            "all encodings failed to parse, fallback UTF-8 preview=${LyricsDebugLog.preview(utf8)}"
        }
        return utf8.takeIf { it.isNotBlank() }
    }

    /** 从 MP3 ID3 USLT 帧读取内嵌歌词 */
    private fun loadFromId3Embedded(songUri: Uri): Lyrics? {
        val bytes = context.contentResolver.openInputStream(songUri)?.use { input ->
            val buffer = ByteArray(ID3_READ_BYTES)
            val read = input.read(buffer)
            if (read <= 0) return@use null
            buffer.copyOf(read)
        } ?: run {
            LyricsDebugLog.d("LyricsLoader.loadFromId3Embedded") { "no mp3 bytes read" }
            return null
        }

        val rawText = Id3LyricsReader.readUsltLyrics(bytes)
        if (rawText == null) {
            LyricsDebugLog.d("LyricsLoader.loadFromId3Embedded") { "no USLT frame in ID3" }
            return null
        }

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> LrcLine(timestampMs = -1L, text = line) }

        if (lines.isEmpty()) return null
        return Lyrics(lines = lines, source = LyricsSource.ID3_EMBEDDED)
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }
}
