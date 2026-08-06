package com.kinny.localmusicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Description: SAF 文件与目录选择器封装
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object SafPickerContracts {

    /** 支持的音频 MIME 类型（尽量宽松，兼容各厂商文件管理器） */
    private val AUDIO_MIME_TYPES = arrayOf(
        "audio/*",
        "audio/mpeg",
        "audio/mp3",
        "application/octet-stream",
    )

    /**
     * 多文件 SAF 选择器，用于导入 MP3 文件
     */
    class OpenMusicDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, input)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
            }

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
            if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
            val clipData = intent.clipData
            return if (clipData != null) {
                buildList {
                    for (i in 0 until clipData.itemCount) {
                        add(clipData.getItemAt(i).uri)
                    }
                }
            } else {
                intent.data?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    /**
     * 目录树 SAF 选择器，用于导入整个音乐文件夹
     */
    class OpenMusicFolderTree(
        private val initialUri: Uri? = StorageUriHelper.primaryStorageRootUri(),
    ) : ActivityResultContract<Unit, Uri?>() {

        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
                // Android 8.0+：打开时定位到手机内部存储，方便用户选择目录
                if (initialUri != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                }
                putExtra(DocumentsContract.EXTRA_PROMPT, context.getString(
                    com.kinny.localmusicplayer.R.string.import_folder_prompt,
                ))
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            if (resultCode != android.app.Activity.RESULT_OK) return null
            return intent?.data
        }
    }

    /** 默认音频文件 MIME 过滤列表 */
    fun defaultAudioMimeTypes(): Array<String> = AUDIO_MIME_TYPES
}

/**
 * Description: SAF 初始目录 Uri 构造工具
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object StorageUriHelper {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** 手机内部存储根目录 primary: */
    fun primaryStorageRootUri(): Uri? =
        DocumentsContract.buildRootUri(EXTERNAL_STORAGE_AUTHORITY, "primary:")

    /** 常用 Music 目录 */
    fun musicFolderUri(): Uri? =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:Music")
}
