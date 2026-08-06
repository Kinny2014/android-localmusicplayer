package com.kinny.localmusicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinny.localmusicplayer.R
import com.kinny.localmusicplayer.data.lyrics.Lyrics
import com.kinny.localmusicplayer.data.lyrics.LyricsSource

/**
 * 歌词展示面板：显示当前播放进度对应的主歌词（英文行）。
 */
@Composable
fun LyricsPanel(
    lyrics: Lyrics,
    currentLyricLine: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp, max = 36.dp)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            lyrics.source == LyricsSource.NONE -> {
                Text(
                    text = stringResource(R.string.no_lyrics),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            currentLyricLine != null -> {
                MarqueeText(
                    text = currentLyricLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
