package com.kinny.localmusicplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kinny.localmusicplayer.di.IoDispatcher
import com.kinny.localmusicplayer.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playlist_store",
)

private val PLAYLIST_KEY = stringPreferencesKey("playlist_json")

/**
 * Description: 播放列表持久化存储，使用 DataStore 与 JSON 序列化
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Singleton
class PlaylistDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 观察播放列表变化 */
    fun observePlaylist(): Flow<List<Song>> =
        context.playlistDataStore.data.map { prefs ->
            val raw = prefs[PLAYLIST_KEY] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<Song>>(raw) }
                .getOrDefault(emptyList())
        }

    /** 保存完整播放列表 */
    suspend fun savePlaylist(songs: List<Song>) = withContext(ioDispatcher) {
        context.playlistDataStore.edit { prefs ->
            prefs[PLAYLIST_KEY] = json.encodeToString(songs)
        }
    }
}
