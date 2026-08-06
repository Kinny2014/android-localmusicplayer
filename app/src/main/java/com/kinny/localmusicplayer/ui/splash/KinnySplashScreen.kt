package com.kinny.localmusicplayer.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

private val SplashDeepPurple = Color(0xFF0D0221)
private val SplashViolet = Color(0xFF4C1D95)
private val SplashAccent = Color(0xFFA78BFA)
private val SplashGlow = Color(0xFFDDD6FE)
private val SplashGold = Color(0xFFFBBF24)

/** Kinny 听启动过渡页：黑胶唱片 + 频谱 + 品牌动效，结束后回调 [onFinished] */
@Composable
fun KinnySplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    splashDurationMs: Long = 3000L,
) {
    var exitPhase by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (exitPhase) 0f else 1f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splashExitAlpha",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (exitPhase) 1.08f else 1f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splashExitScale",
    )

    val logoScale = remember { Animatable(0.2f) }
    val logoAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }

    val context = LocalContext.current
    val greetingPlayer = remember { SplashGreetingPlayer(context) }

    DisposableEffect(greetingPlayer) {
        onDispose { greetingPlayer.shutdown() }
    }

    LaunchedEffect(greetingPlayer) {
        delay(350.milliseconds)
        greetingPlayer.playWelcome()
    }

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            logoAlpha.animateTo(1f, tween(700))
        }
        launch {
            delay(500.milliseconds)
            subtitleAlpha.animateTo(1f, tween(600))
        }
        delay(splashDurationMs.milliseconds)
        exitPhase = true
        greetingPlayer.stop()
        delay(560.milliseconds)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(contentScale)
            .alpha(contentAlpha)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SplashDeepPurple,
                        Color(0xFF1E0A3C),
                        SplashViolet,
                        Color(0xFF2E1065),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        SplashBackgroundEffects()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            ) {
                VinylDisc(modifier = Modifier.size(200.dp))
                KinnyLogoBadge(modifier = Modifier.size(88.dp))
            }

            Spacer(modifier = Modifier.height(28.dp))

            EqualizerBars(
                modifier = Modifier
                    .height(48.dp)
                    .alpha(logoAlpha.value),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(subtitleAlpha.value),
            ) {
                Text(
                    text = "Kinny",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 4.sp,
                )
                Text(
                    text = "听",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = SplashGlow,
                    letterSpacing = 12.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本地音乐 · 同步歌词",
                    fontSize = 13.sp,
                    color = SplashAccent.copy(alpha = 0.85f),
                    letterSpacing = 2.sp,
                )
            }
        }

        FloatingNotes(modifier = Modifier.fillMaxSize())
    }
}

/** 背景脉冲光晕与扩散圆环 */
@Composable
private fun SplashBackgroundEffects() {
    val infinite = rememberInfiniteTransition(label = "splashBg")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ringProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.42f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SplashAccent.copy(alpha = 0.35f),
                    SplashViolet.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                center = center,
                radius = size.minDimension * 0.55f * pulse,
            ),
            radius = size.minDimension * 0.55f * pulse,
            center = center,
        )
        val ringRadius = size.minDimension * (0.25f + ringProgress * 0.35f)
        drawCircle(
            color = SplashAccent.copy(alpha = (1f - ringProgress) * 0.4f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** 旋转黑胶唱片 */
@Composable
private fun VinylDisc(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vinylRotation",
    )

    Canvas(
        modifier = modifier.rotate(rotation),
    ) {
        val radius = size.minDimension / 2f
        drawCircle(color = Color(0xFF0A0A0A), radius = radius)
        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = radius * 0.95f,
            style = Stroke(width = 1.5f),
        )
        // 沟槽纹理
        for (i in 1..8) {
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = radius * (0.3f + i * 0.08f),
                style = Stroke(width = 1f),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SplashViolet, SplashDeepPurple),
            ),
            radius = radius * 0.28f,
        )
        drawCircle(color = SplashDeepPurple, radius = radius * 0.06f)
    }
}

/** 中心 K 字品牌徽标 */
@Composable
private fun KinnyLogoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(SplashAccent, SplashViolet, Color(0xFF6D28D9)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "K",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}

/** 动态频谱条 */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val barCount = 9
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(barCount) { index ->
            val infinite = rememberInfiniteTransition(label = "bar$index")
            val fraction by infinite.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 380 + index * 40,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "barFraction$index",
            )
            val barColor = Brush.verticalGradient(
                colors = listOf(SplashGold, SplashAccent, SplashGlow),
            )
            Box(
                modifier = Modifier
                    .size(width = 7.dp, height = (48 * fraction).dp)
                    .background(barColor, CircleShape),
            )
        }
    }
}

/** 漂浮音符粒子 */
@Composable
private fun FloatingNotes(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "notes")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "notePhase",
    )

    val noteSpecs = remember {
        listOf(
            Triple(0.15f, 0.2f, 28.dp),
            Triple(0.82f, 0.15f, 22.dp),
            Triple(0.1f, 0.65f, 20.dp),
            Triple(0.88f, 0.7f, 26.dp),
            Triple(0.5f, 0.08f, 18.dp),
        )
    }

    Box(modifier = modifier) {
        noteSpecs.forEachIndexed { index, (xFrac, yFrac, size) ->
            val floatY = sin(phase + index * 1.2f) * 18f
            val alpha = 0.25f + (sin(phase + index) + 1f) / 2f * 0.45f
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SplashGlow.copy(alpha = alpha),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (xFrac * 320).dp,
                        y = (yFrac * 640).dp + floatY.dp,
                    )
                    .size(size)
                    .blur(0.5.dp),
            )
        }
    }
}
