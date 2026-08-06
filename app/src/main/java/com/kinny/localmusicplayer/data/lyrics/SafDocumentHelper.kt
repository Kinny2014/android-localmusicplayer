package com.kinny.localmusicplayer.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Description: SAF 文档 Uri 工具，兼容路径型与 Downloads opaque ID
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
internal object SafDocumentHelper {

    /** 从 tree/document Uri 还原用户授权的 tree Uri */
    fun resolveTreeUri(documentUri: Uri, sourceTreeUri: Uri? = null): Uri? {
        sourceTreeUri?.let { return it }
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(
                documentUri.authority,
                DocumentsContract.getTreeDocumentId(documentUri),
            )
        }.getOrNull()
    }

    /**
     * 推断「同目录兄弟文件」所在的父文件夹 documentId。
     *
     * - 路径型 ID（primary:Download/kinny_music/xx.mp3）→ primary:Download/kinny_music
     * - Downloads opaque ID（msf:1000106457）→ 回退为 tree 根目录 msd:1000106454
     */
    fun parentFolderIds(context: Context, songUri: Uri): List<String> {
        if (!DocumentsContract.isDocumentUri(context, songUri)) return emptyList()
        val documentId = DocumentsContract.getDocumentId(songUri)
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(songUri)
        }.getOrNull() ?: return emptyList()

        val pathParent = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        return buildList {
            if (pathParent.isNotEmpty() && pathParent != documentId) {
                add(pathParent)
            }
            // Downloads Provider：文件通常是授权目录的直接子项
            if (treeDocumentId.isNotBlank() && treeDocumentId != documentId) {
                add(treeDocumentId)
            }
        }.distinct()
    }

    /** 从「艺术家 - 歌名」格式文件名提取歌名部分，如「刀郎 - 花妖」→「花妖」 */
    fun extractTitleAfterArtistDash(name: String): String? {
        val separator = " - "
        val index = name.indexOf(separator)
        if (index < 0) return null
        return name.substring(index + separator.length).trim().takeIf { it.isNotBlank() }
    }

    /** 生成用于 LRC 文件名匹配的名称候选 */
    fun lrcNameCandidates(vararg names: String?): List<String> {
        return names.filterNotNull().flatMap { name ->
            val trimmed = name.trim()
            val normalized = trimmed.replace(Regex("""^\d+\s*[.\-_]\s*"""), "").trim()
            buildList {
                add(trimmed)
                if (normalized.isNotBlank()) add(normalized)
                extractTitleAfterArtistDash(trimmed)?.let { add(it) }
                extractTitleAfterArtistDash(normalized)?.let { add(it) }
            }
        }.distinct().filter { it.isNotBlank() }
    }
}
