package com.kinny.localmusicplayer.ui.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinny.localmusicplayer.R
import com.kinny.localmusicplayer.model.PlaylistUiEvent
import com.kinny.localmusicplayer.ui.components.LyricsPanel
import com.kinny.localmusicplayer.ui.components.PlayerControlBar
import com.kinny.localmusicplayer.ui.components.ReorderableSongList
import com.kinny.localmusicplayer.util.PermissionHelper
import com.kinny.localmusicplayer.util.SafPickerContracts

/**
 * 播放列表主屏幕：通过 SAF 系统文档选择器导入手机目录/文件，无需存储权限。
 */
@Composable
fun PlaylistScreen(
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 通知权限（可选，不阻塞导入）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 导入功能不依赖此权限 */ }

    // SAF 多文件选择
    val filePickerLauncher = rememberLauncherForActivityResult(
        SafPickerContracts.OpenMusicDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    // SAF 目录树选择 — 直接打开手机文件管理器选目录
    val folderPickerLauncher = rememberLauncherForActivityResult(
        SafPickerContracts.OpenMusicFolderTree(),
    ) { uri ->
        uri?.let { viewModel.importFolder(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.updatePermissionState(PermissionHelper.hasStoragePermission(context))
        PermissionHelper.notificationPermission()?.let { permission ->
            if (!PermissionHelper.hasNotificationPermission(context)) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    PlaylistContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onImportFiles = {
            filePickerLauncher.launch(SafPickerContracts.defaultAudioMimeTypes())
        },
        onImportFolder = {
            folderPickerLauncher.launch(Unit)
        },
        onPlaySong = { song ->
            viewModel.playSong(song)
            onNavigateToNowPlaying()
        },
        onOpenNowPlaying = {
            if (uiState.currentSongId != null) onNavigateToNowPlaying()
        },
        onRemoveSong = viewModel::removeSong,
        onMoveSong = viewModel::moveSong,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSkipNext = viewModel::skipToNext,
        onSkipPrevious = viewModel::skipToPrevious,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistContent(
    uiState: com.kinny.localmusicplayer.model.PlaylistUiState,
    snackbarHostState: SnackbarHostState,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onPlaySong: (com.kinny.localmusicplayer.model.Song) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong = uiState.songs.find { it.id == uiState.currentSongId }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.playlist_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                modifier = if (currentSong == null) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                },
            ) {
                FloatingActionButton(
                    onClick = onImportFolder,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.import_folder),
                    )
                }
                FloatingActionButton(onClick = onImportFiles) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.import_files),
                    )
                }
            }
        },
        bottomBar = {
            if (currentSong != null) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    LyricsPanel(
                        lyrics = uiState.currentLyrics,
                        currentLyricLine = uiState.currentLyricLine,
                    )
                    PlayerControlBar(
                        currentSong = currentSong,
                        isPlaying = uiState.isPlaying,
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onBarClick = onOpenNowPlaying,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.songs.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.empty_playlist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.import_folder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onImportFolder) {
                            Text(stringResource(R.string.import_folder))
                        }
                        OutlinedButton(onClick = onImportFiles) {
                            Text(stringResource(R.string.import_files))
                        }
                    }
                }
                else -> {
                    ReorderableSongList(
                        songs = uiState.songs,
                        currentSongId = uiState.currentSongId,
                        onPlaySong = onPlaySong,
                        onRemoveSong = onRemoveSong,
                        onMoveSong = onMoveSong,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
