package com.kinny.localmusicplayer.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Description: 系统状态栏与导航栏外观控制（Edge-to-Edge 下图标可见性）
 * Author: kinny
 * Created: 2026/8/5 16:39
 */
@Composable
fun SystemBarAppearance(
    /** true = 深色图标（浅色背景）；false = 浅色图标（深色背景） */
    useDarkStatusBarIcons: Boolean = !isSystemInDarkTheme(),
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkStatusBarIcons
            isAppearanceLightNavigationBars = useDarkStatusBarIcons
        }
    }
}
