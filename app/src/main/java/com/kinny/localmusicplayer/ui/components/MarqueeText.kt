package com.kinny.localmusicplayer.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Description: 过长单行文本自动横向滚动（跑马灯）
 * Author: kinny
 * Created: 2026/8/5 16:32
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 1_200,
                initialDelayMillis = 800,
            ),
        style = style,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}
