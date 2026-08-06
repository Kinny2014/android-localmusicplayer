package com.kinny.localmusicplayer.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Description: SAF 同目录兄弟文件查找器，用于匹配 .lrc 歌词
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
internal object SafSiblingFinder {

    /**
     * 查找与 [songUri] 同目录、主文件名与 [baseName] 匹配（忽略大小写）的 .lrc 文件。
     *
     * @param sourceTreeUri 目录导入时用户授权的 tree Uri，可避免嵌套路径下 tree 解析错误
     */
    fun findLrcUri(
        context: Context,
        songUri: Uri,
        baseName: String,
        sourceTreeUri: Uri? = null,
    ): Uri? {
        val effectiveTreeUri = SafDocumentHelper.resolveTreeUri(songUri, sourceTreeUri)
        LyricsDebugLog.d("SafSiblingFinder.findLrcUri.start") {
            "baseName=$baseName, songUri=$songUri, sourceTreeUri=$effectiveTreeUri"
        }
        val normalizedNames = SafDocumentHelper.lrcNameCandidates(baseName)
        for (name in normalizedNames) {
            findLrcViaDocumentsContract(context, songUri, name, effectiveTreeUri)?.let {
                LyricsDebugLog.d("SafSiblingFinder.findLrcUri") { "found via DocumentsContract: $it" }
                return it
            }
        }
        for (name in normalizedNames) {
            findLrcViaDocumentFile(context, songUri, name)?.let {
                LyricsDebugLog.d("SafSiblingFinder.findLrcUri") { "found via DocumentFile: $it" }
                return it
            }
        }
        LyricsDebugLog.w("SafSiblingFinder.findLrcUri") { "not found for baseName=$baseName" }
        return null
    }

    /** 通过 DocumentsContract 在父目录中查找（适用于目录树导入） */
    private fun findLrcViaDocumentsContract(
        context: Context,
        songUri: Uri,
        baseName: String,
        treeUri: Uri?,
    ): Uri? {
        if (!DocumentsContract.isDocumentUri(context, songUri)) {
            LyricsDebugLog.d("SafSiblingFinder.DocumentsContract") { "not a document uri: $songUri" }
            return null
        }

        if (treeUri == null) {
            LyricsDebugLog.w("SafSiblingFinder.DocumentsContract") { "treeUri resolve failed" }
            return null
        }

        val resolver = context.contentResolver
        val documentId = DocumentsContract.getDocumentId(songUri)
        val parentIds = SafDocumentHelper.parentFolderIds(context, songUri)

        if (parentIds.isEmpty()) {
            LyricsDebugLog.w("SafSiblingFinder.DocumentsContract") {
                "no parent folder candidates, documentId=$documentId"
            }
            return null
        }

        LyricsDebugLog.d("SafSiblingFinder.DocumentsContract") {
            "treeUri=$treeUri, documentId=$documentId, parentIds=$parentIds, baseName=$baseName"
        }

        for (parentId in parentIds) {
            val result = queryLrcInFolder(resolver, treeUri, parentId, baseName)
            if (result != null) return result
        }
        return null
    }

    private fun queryLrcInFolder(
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        parentId: String,
        baseName: String,
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val siblingNames = mutableListOf<String>()
        var result: Uri? = null

        try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idIndex < 0) {
                    LyricsDebugLog.w("SafSiblingFinder.DocumentsContract") { "cursor missing document id column" }
                    return null
                }

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idIndex) ?: continue
                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (displayName != null) {
                        siblingNames.add(displayName)
                        if (isMatchingLrc(displayName, baseName)) {
                            result = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        }
                    }
                }
            } ?: LyricsDebugLog.w("SafSiblingFinder.DocumentsContract") {
                "query returned null cursor, parentId=$parentId"
            }
        } catch (e: Exception) {
            LyricsDebugLog.e("SafSiblingFinder.DocumentsContract.queryFailed", e) {
                "parentId=$parentId"
            }
            return null
        }

        LyricsDebugLog.d("SafSiblingFinder.DocumentsContract.siblings") {
            "parentId=$parentId, count=${siblingNames.size}, names=${siblingNames.joinToString()}, match=${result != null}"
        }
        return result
    }

    /** 通过 DocumentFile.parentFile 查找（单文件导入时的降级方案） */
    private fun findLrcViaDocumentFile(
        context: Context,
        songUri: Uri,
        baseName: String,
    ): Uri? {
        val mp3Doc = DocumentFile.fromSingleUri(context, songUri)
        if (mp3Doc == null) {
            LyricsDebugLog.d("SafSiblingFinder.DocumentFile") { "fromSingleUri returned null" }
            return null
        }
        val parent = mp3Doc.parentFile
        if (parent == null) {
            LyricsDebugLog.d("SafSiblingFinder.DocumentFile") { "parentFile is null" }
            return null
        }

        val lrcCandidates = SafDocumentHelper.lrcNameCandidates(baseName)
            .flatMap { listOf("$it.lrc", "$it.LRC") }
        val lrcFile = lrcCandidates.firstNotNullOfOrNull { name -> parent.findFile(name) }
            ?: parent.listFiles().firstOrNull { doc ->
                doc.isFile && doc.name != null && isMatchingLrc(doc.name!!, baseName)
            }

        if (lrcFile == null) {
            val siblings = parent.listFiles().mapNotNull { it.name }
            LyricsDebugLog.d("SafSiblingFinder.DocumentFile") {
                "no match, siblings=${siblings.joinToString()}"
            }
            return null
        }
        return lrcFile.uri
    }

    private fun isMatchingLrc(displayName: String, baseName: String): Boolean {
        if (!displayName.endsWith(".lrc", ignoreCase = true)) return false
        val lrcBase = displayName.substringBeforeLast('.')
        return SafDocumentHelper.lrcNameCandidates(baseName).any { candidate ->
            lrcBase.equals(candidate, ignoreCase = true)
        }
    }
}
