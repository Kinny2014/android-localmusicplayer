package com.kinny.localmusicplayer.data

import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import com.kinny.localmusicplayer.di.IoDispatcher
import com.kinny.localmusicplayer.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Description: 播放列表仓库接口，负责歌曲增删、排序与导入
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
interface PlaylistRepository {
    fun observePlaylist(): Flow<List<Song>>
    suspend fun addSongs(songs: List<Song>): Int
    suspend fun removeSong(songId: String): Preferences
    suspend fun savePlaylistOrder(songs: List<Song>): Preferences
    suspend fun importFiles(uris: List<Uri>, onFilePermission: (Uri) -> Unit): Result<Int>
    suspend fun importFolder(treeUri: Uri, onTreePermission: (Uri) -> Unit): Result<Int>
}

/**
 * Description: 播放列表仓库实现类
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dataStore: PlaylistDataStore,
    private val scanner: LocalMusicScanner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    override fun observePlaylist(): Flow<List<Song>> = dataStore.observePlaylist()

    override suspend fun addSongs(songs: List<Song>): Int = withContext(ioDispatcher) {
        if (songs.isEmpty()) return@withContext 0
        val current = dataStore.observePlaylist().first()
        val existingIds = current.map { it.id }.toSet()
        val merged = current.map { existing ->
            songs.find { it.id == existing.id }?.let { imported ->
                existing.copy(
                    title = imported.title,
                    artist = imported.artist,
                    durationMs = imported.durationMs,
                    lrcUri = imported.lrcUri ?: existing.lrcUri,
                    sourceTreeUri = imported.sourceTreeUri ?: existing.sourceTreeUri,
                )
            } ?: existing
        }
        val brandNew = songs.filterNot { it.id in existingIds }
        dataStore.savePlaylist(merged + brandNew)
        brandNew.size
    }

    override suspend fun removeSong(songId: String) = withContext(ioDispatcher) {
        val current = dataStore.observePlaylist().first()
        dataStore.savePlaylist(current.filterNot { it.id == songId })
    }

    /** 保存拖拽重排后的播放列表顺序 */
    override suspend fun savePlaylistOrder(songs: List<Song>) = withContext(ioDispatcher) {
        dataStore.savePlaylist(songs)
    }

    override suspend fun importFiles(
        uris: List<Uri>,
        onFilePermission: (Uri) -> Unit,
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            uris.forEach { uri -> onFilePermission(uri) }
            val songs = scanner.scanFiles(uris)
            addSongs(songs)
        }
    }

    override suspend fun importFolder(
        treeUri: Uri,
        onTreePermission: (Uri) -> Unit,
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            onTreePermission(treeUri)
            val songs = scanner.scanFolder(treeUri)
            addSongs(songs)
        }
    }
}
