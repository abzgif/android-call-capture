package com.callcapture.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "WaveformAnimation")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.height(32.dp).width((barCount * 6).dp)) {
        val totalWidth = size.width
        val barWidth = 3.dp.toPx()
        val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1)
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            val wave = if (isPlaying) {
                val factor = Math.sin((normalizedIndex * Math.PI * 2) + (animatedPhase * Math.PI)).toFloat()
                (0.3f + 0.7f * Math.abs(factor)).coerceIn(0.15f, 1f)
            } else {
                0.2f + 0.3f * Math.abs(Math.sin(normalizedIndex * Math.PI).toFloat())
            }

            val barHeight = maxHeight * wave
            val startX = i * (barWidth + spacing)
            val startY = (maxHeight - barHeight) / 2

            drawRoundRect(
                color = if (isPlaying) color else color.copy(alpha = 0.4f),
                topLeft = Offset(startX, startY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
