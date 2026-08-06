package com.kinny.localmusicplayer.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * Description: SAF Uri 访问权限持久化工具
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object UriPermissionHelper {

    /** 持久化单个文件的读取权限 */
    fun persistFileReadPermission(contentResolver: ContentResolver, uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /**
     * 持久化目录树的访问权限。
     *
     * 目录树需要同时申请 READ + WRITE 持久化权限（Android 官方 SAF 要求），
     * 否则部分机型上无法递归读取子文件。
     */
    fun persistTreeReadPermission(contentResolver: ContentResolver, treeUri: Uri) {
        val readWriteFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val result = runCatching {
            contentResolver.takePersistableUriPermission(treeUri, readWriteFlags)
        }
        if (result.isFailure) {
            // 部分设备仅支持 READ 持久化，降级重试
            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
