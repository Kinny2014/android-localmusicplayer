package com.kinny.localmusicplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kinny.localmusicplayer.ui.navigation.AppNavHost
import com.kinny.localmusicplayer.ui.splash.KinnySplashScreen
import com.kinny.localmusicplayer.ui.theme.LocalMusicTheme
import com.kinny.localmusicplayer.ui.theme.SystemBarAppearance
import dagger.hilt.android.AndroidEntryPoint

/**
 * Description: 应用主 Activity，启动过渡动画后进入主界面
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        setContent {
            LocalMusicTheme(dynamicColor = false) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                val darkTheme = isSystemInDarkTheme()
                // 启动页深紫背景用浅色状态栏图标；主界面随深浅色主题切换
                SystemBarAppearance(
                    useDarkStatusBarIcons = !showSplash && !darkTheme,
                )

                AnimatedVisibility(
                    visible = showSplash,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    KinnySplashScreen(
                        onFinished = { showSplash = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AnimatedVisibility(
                    visible = !showSplash,
                    enter = fadeIn(),
                ) {
                    AppNavHost(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
