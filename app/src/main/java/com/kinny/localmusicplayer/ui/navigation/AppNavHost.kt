package com.kinny.localmusicplayer.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kinny.localmusicplayer.ui.nowplaying.NowPlayingScreen
import com.kinny.localmusicplayer.ui.playlist.PlaylistScreen
import com.kinny.localmusicplayer.ui.playlist.PlaylistViewModel

private const val ROUTE_PLAYLIST = "playlist"
private const val ROUTE_NOW_PLAYING = "now_playing"

/**
 * 应用导航：播放列表 ↔ 全屏播放页，共享同一个 [PlaylistViewModel]。
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_PLAYLIST,
        modifier = modifier,
    ) {
        composable(ROUTE_PLAYLIST) { playlistEntry ->
            PlaylistScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(playlistEntry),
                onNavigateToNowPlaying = {
                    navController.navigate(ROUTE_NOW_PLAYING)
                },
            )
        }

        composable(ROUTE_NOW_PLAYING) { nowPlayingEntry ->
            // 与播放列表共享 ViewModel，保持播放状态与歌词同步
            val playlistEntry = remember(nowPlayingEntry) {
                navController.getBackStackEntry(ROUTE_PLAYLIST)
            }
            NowPlayingScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(playlistEntry),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
