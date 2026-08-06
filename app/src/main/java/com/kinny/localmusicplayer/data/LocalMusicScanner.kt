package com.kinny.localmusicplayer.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.kinny.localmusicplayer.data.lyrics.LyricsDebugLog
import com.kinny.localmusicplayer.data.lyrics.SafDocumentHelper
import com.kinny.localmusicplayer.di.IoDispatcher
import com.kinny.localmusicplayer.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** MP3 文件 MIME 类型 */
private const val MIME_AUDIO_MPEG = "audio/mpeg"
private const val MIME_AUDIO_MP3 = "audio/mp3"

/**
 * Description: 扫描目录树时收集到的单个文件条目
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
private data class TreeFileEntry(
    val uri: Uri,
    val documentId: String,
    val parentDocumentId: String,
    val displayName: String,
    val mimeType: String?,
)

/**
 * Description: 本地音乐扫描器，从 SAF 文件或文件夹提取 MP3 元数据并配对 LRC
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 从多个文件 Uri 中提取歌曲信息，自动去重并过滤非 MP3 文件。
     */
    suspend fun scanFiles(uris: List<Uri>): List<Song> = withContext(ioDispatcher) {
        uris.mapNotNull { uri -> extractSongFromUri(uri) }
            .distinctBy { it.id }
    }

    /**
     * 递归扫描 SAF 目录树内所有 MP3，并在扫描阶段配对同目录 .lrc。
     *
     * 导入时一次性索引歌词，避免播放时再通过 parentFile / 兄弟查询（在
     * Download/kinny_music 等嵌套目录下经常失败）。
     */
    suspend fun scanFolder(treeUri: Uri): List<Song> = withContext(ioDispatcher) {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        LyricsDebugLog.d("LocalMusicScanner.scanFolder.start") {
            "treeUri=$treeUri, treeDocumentId=$treeDocumentId"
        }
        val entries = mutableListOf<TreeFileEntry>()
        walkDocumentTree(resolver, treeUri, treeDocumentId, entries)

        LyricsDebugLog.d("LocalMusicScanner.scanFolder.scanned") {
            val mp3Count = entries.count { isAudioFile(it.mimeType, it.displayName) }
            val lrcCount = entries.count { it.displayName.endsWith(".lrc", ignoreCase = true) }
            "totalFiles=${entries.size}, mp3=$mp3Count, lrc=$lrcCount"
        }

        if (entries.isNotEmpty()) {
            return@withContext buildSongsFromTreeEntries(entries, treeUri.toString())
        }

        LyricsDebugLog.w("LocalMusicScanner.scanFolder") { "DocumentsContract scan empty, fallback DocumentFile" }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        collectMp3Files(root)
            .mapNotNull { file ->
                extractSongFromUri(
                    uri = file.uri,
                    sourceTreeUri = treeUri.toString(),
                )
            }
            .distinctBy { it.id }
    }

    private fun buildSongsFromTreeEntries(
        entries: List<TreeFileEntry>,
        sourceTreeUri: String,
    ): List<Song> {
        val lrcByParent = entries
            .filter { it.displayName.endsWith(".lrc", ignoreCase = true) }
            .groupBy { it.parentDocumentId }
            .mapValues { (_, lrcFiles) -> buildLrcIndex(lrcFiles) }

        LyricsDebugLog.d("LocalMusicScanner.buildSongs") {
            val lrcSummary = lrcByParent.entries.joinToString(" | ") { (parent, map) ->
                "$parent -> [${map.keys.joinToString()}]"
            }
            "lrcIndex=$lrcSummary"
        }

        return entries
            .filter { isAudioFile(it.mimeType, it.displayName) }
            .mapNotNull { entry ->
                val baseName = entry.displayName.substringBeforeLast('.')
                val parentLrcKeys = lrcByParent[entry.parentDocumentId]?.keys?.joinToString() ?: "none"
                val song = extractSongFromUri(
                    uri = entry.uri,
                    sourceTreeUri = sourceTreeUri,
                )
                val matchedLrc = song?.let { findMatchingLrc(baseName, lrcByParent[entry.parentDocumentId], it.title) }
                LyricsDebugLog.d("LocalMusicScanner.matchLrc") {
                    "mp3=${entry.displayName}, baseName=$baseName, title=${song?.title}, parent=${entry.parentDocumentId}, " +
                        "parentLrcKeys=[$parentLrcKeys], matched=${matchedLrc?.displayName ?: "NONE"}"
                }
                song?.copy(
                    lrcUri = matchedLrc?.uri?.toString(),
                )
            }
            .distinctBy { it.id }
    }

    /**
     * 递归遍历目录树，收集所有文件（含 .lrc），而不只收集 MP3。
     */
    private fun walkDocumentTree(
        resolver: ContentResolver,
        treeUri: Uri,
        documentId: String,
        result: MutableList<TreeFileEntry>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idIndex < 0) return

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIndex) ?: continue
                val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    ?: continue

                when {
                    mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                        walkDocumentTree(resolver, treeUri, childDocId, result)
                    }
                    else -> {
                        result.add(
                            TreeFileEntry(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId),
                                documentId = childDocId,
                                parentDocumentId = childDocId.substringBeforeLast('/', missingDelimiterValue = documentId),
                                displayName = displayName,
                                mimeType = mimeType,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** 递归收集文件夹内所有 MP3 文件（DocumentFile 降级方案） */
    private fun collectMp3Files(dir: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        for (file in dir.listFiles()) {
            when {
                file.isDirectory -> result.addAll(collectMp3Files(file))
                file.isFile && isAudioFile(file.type, file.name) -> result.add(file)
            }
        }
        return result
    }

    /** 判断是否为 MP3 / 音频文件 */
    private fun isAudioFile(mimeType: String?, displayName: String?): Boolean {
        if (displayName?.endsWith(".mp3", ignoreCase = true) == true) return true
        if (mimeType == null) return false
        return mimeType == MIME_AUDIO_MPEG ||
            mimeType == MIME_AUDIO_MP3 ||
            mimeType.startsWith("audio/")
    }

    /** 从单个 Uri 提取歌曲元数据 */
    private fun extractSongFromUri(
        uri: Uri,
        lrcUri: String? = null,
        sourceTreeUri: String? = null,
    ): Song? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: return null

        if (!isAudioFile(resolver.getType(uri), displayName)) {
            return null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            Song(
                id = uri.toString(),
                title = title,
                artist = artist,
                durationMs = duration,
                uri = uri.toString(),
                lrcUri = lrcUri,
                sourceTreeUri = sourceTreeUri ?: inferSourceTreeUri(uri),
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** 从 tree/document Uri 推断 sourceTreeUri（兼容旧数据） */
    private fun inferSourceTreeUri(uri: Uri): String? {
        return SafDocumentHelper.resolveTreeUri(uri)?.toString()
    }

    /** 查询文件显示名称 */
    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }

    /** 为同目录 LRC 建立多种文件名键（原始 / 去序号前缀 / 艺术家-歌名），便于与 MP3 匹配 */
    private fun buildLrcIndex(lrcFiles: List<TreeFileEntry>): Map<String, TreeFileEntry> {
        return buildMap {
            for (file in lrcFiles) {
                val base = file.displayName.substringBeforeLast('.')
                for (key in SafDocumentHelper.lrcNameCandidates(base)) {
                    put(key.lowercase(), file)
                }
            }
        }
    }

    /** 在同目录 LRC 索引中查找与 MP3 主文件名匹配的条目 */
    private fun findMatchingLrc(
        mp3BaseName: String,
        lrcIndex: Map<String, TreeFileEntry>?,
        title: String? = null,
    ): TreeFileEntry? {
        if (lrcIndex == null) return null
        val candidates = SafDocumentHelper.lrcNameCandidates(mp3BaseName, title)
            .map { it.lowercase() }
            .distinct()
        for (key in candidates) {
            lrcIndex[key]?.let { return it }
        }
        return null
    }
}
