package com.kinny.localmusicplayer.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinny.localmusicplayer.R
import com.kinny.localmusicplayer.data.lyrics.LrcLine
import com.kinny.localmusicplayer.data.lyrics.Lyrics

/** 歌词行间距 */
private val LYRICS_LINE_SPACING = 20.dp

/**
 * 可滚动的同步歌词列表。
 *
 * - LRC 时间轴歌词：高亮当前英文行，并自动滚动跟随播放进度
 * - 无时间轴歌词：静态展示全部文本
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ScrollableLyrics(
    lyrics: Lyrics,
    currentLineIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (lyrics.lines.isEmpty()) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.no_lyrics),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        // 上下各留半屏，使首尾行也能滚到视区中央，避免底部歌词被截断
        val centerPadding = maxHeight / 2

        LaunchedEffect(currentLineIndex, lyrics.isSynced, maxHeight) {
            if (lyrics.isSynced && currentLineIndex >= 0) {
                listState.animateScrollToItem(currentLineIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = centerPadding,
                bottom = centerPadding,
                start = 28.dp,
                end = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LYRICS_LINE_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "$index-${line.timestampMs}-${line.text}-${line.translation}" },
            ) { index, line ->
                LyricLineItem(
                    line = line,
                    isActive = lyrics.isSynced && index == currentLineIndex,
                )
            }
        }
    }
}

/** 单行歌词：仅高亮英文主歌词；翻译次要展示且不高亮 */
@Composable
private fun LyricLineItem(
    line: LrcLine,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        },
        animationSpec = tween(durationMillis = 280),
        label = "lyricColor",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (isActive) 22.sp else 17.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                lineHeight = if (isActive) 30.sp else 26.sp,
            ),
            color = color,
            textAlign = TextAlign.Center,
            softWrap = true,
        )
        if (!line.translation.isNullOrBlank()) {
            Text(
                text = line.translation,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (isActive) 15.sp else 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isActive) 0.72f else 0.48f,
                ),
                textAlign = TextAlign.Center,
                softWrap = true,
            )
        }
    }
}
