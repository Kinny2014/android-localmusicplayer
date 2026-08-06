package com.kinny.localmusicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kinny.localmusicplayer.R
import com.kinny.localmusicplayer.model.Song
import com.kinny.localmusicplayer.util.formatDurationMs
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 支持拖拽排序的歌曲列表。
 */
@Composable
fun ReorderableSongList(
    songs: List<Song>,
    currentSongId: String?,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onMoveSong: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveSong(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = songs,
            key = { it.id },
        ) { song ->
            ReorderableItem(
                state = reorderableState,
                key = song.id,
            ) { isDragging ->
                ReorderableSongRow(
                    song = song,
                    isPlaying = song.id == currentSongId,
                    isDragging = isDragging,
                    onClick = { onPlaySong(song) },
                    onRemove = { onRemoveSong(song.id) },
                )
            }
        }
    }
}

/**
 * 可长按拖动的歌曲条目，整行统一背景色。
 */
@Composable
private fun ReorderableCollectionItemScope.ReorderableSongRow(
    song: Song,
    isPlaying: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val backgroundColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            // 长按整行开始拖拽排序
            .longPressDraggableHandle(),
        color = backgroundColor,
        tonalElevation = if (isDragging) 6.dp else 0.dp,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = song.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = formatDurationMs(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.semantics {
                    contentDescription = "移除 ${song.title}"
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_song),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
