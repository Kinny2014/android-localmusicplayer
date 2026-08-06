package com.kinny.localmusicplayer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Description: 权限工具类，检查并获取存储与通知权限
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object PermissionHelper {

    /** 读取本地音频所需的运行时权限 */
    fun storagePermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    /** 通知权限（Android 13+） */
    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /** 检查是否已授予存储权限 */
    fun hasStoragePermission(context: Context): Boolean =
        storagePermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** 检查是否已授予通知权限 */
    fun hasNotificationPermission(context: Context): Boolean {
        val permission = notificationPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 获取所有需要请求的权限 */
    fun allRequiredPermissions(): Array<String> = buildList {
        addAll(storagePermissions())
        notificationPermission()?.let { add(it) }
    }.toTypedArray()
}
