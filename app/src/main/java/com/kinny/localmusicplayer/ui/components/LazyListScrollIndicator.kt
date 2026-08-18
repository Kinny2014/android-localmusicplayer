package com.kinny.localmusicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ScrollbarWidth = 4.dp
private val ScrollbarEndPadding = 4.dp
private val MinThumbHeightFraction = 0.12f

/**
 * 垂直列表滚动进度指示条：滑动时显示当前位置，停止滑动后渐隐。
 */
@Composable
fun LazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
) {
    val scrollMetrics by remember {
        derivedStateOf { calculateScrollMetrics(listState) }
    }

    var visible by remember { mutableStateOf(false) }
    var targetAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, scrollMetrics.isScrollable) {
        if (!scrollMetrics.isScrollable) {
            targetAlpha = 0f
            visible = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress) {
            visible = true
            targetAlpha = 1f
        } else {
            delay(700)
            targetAlpha = 0f
            delay(250)
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 220),
        label = "scrollbarAlpha",
    )

    if (!visible && alpha <= 0.01f) return
    if (!scrollMetrics.isScrollable) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollbarWidth + ScrollbarEndPadding)
            .padding(end = ScrollbarEndPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(ScrollbarWidth),
        ) {
            val trackWidth = size.width
            val trackHeight = size.height
            val corner = CornerRadius(trackWidth / 2f, trackWidth / 2f)

            drawRoundRect(
                color = trackColor.copy(alpha = trackColor.alpha * alpha),
                size = Size(trackWidth, trackHeight),
                cornerRadius = corner,
            )

            val thumbHeight = (trackHeight * scrollMetrics.thumbHeightFraction)
                .coerceAtLeast(trackWidth * 2f)
            val maxThumbOffset = (trackHeight - thumbHeight).coerceAtLeast(0f)
            val thumbOffset = maxThumbOffset * scrollMetrics.progress

            drawRoundRect(
                color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                topLeft = Offset(0f, thumbOffset),
                size = Size(trackWidth, thumbHeight),
                cornerRadius = corner,
            )
        }
    }
}

private data class ScrollMetrics(
    val progress: Float = 0f,
    val thumbHeightFraction: Float = 1f,
    val isScrollable: Boolean = false,
)

private fun calculateScrollMetrics(listState: LazyListState): ScrollMetrics {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return ScrollMetrics()

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return ScrollMetrics()

    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return ScrollMetrics()

    val averageItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
    if (averageItemHeight <= 0f) return ScrollMetrics()

    val estimatedTotalHeight = averageItemHeight * totalItems
    if (estimatedTotalHeight <= viewportHeight) {
        return ScrollMetrics(isScrollable = false)
    }

    val scrolled = listState.firstVisibleItemIndex * averageItemHeight +
        listState.firstVisibleItemScrollOffset
    val maxScroll = estimatedTotalHeight - viewportHeight
    val progress = (scrolled / maxScroll).coerceIn(0f, 1f)
    val thumbHeightFraction = (viewportHeight / estimatedTotalHeight)
        .coerceIn(MinThumbHeightFraction, 1f)

    return ScrollMetrics(
        progress = progress,
        thumbHeightFraction = thumbHeightFraction,
        isScrollable = true,
    )
}
