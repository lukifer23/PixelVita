package com.example.androiddiffusion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    particleColor: Color = MaterialTheme.colorScheme.primary
) {
    val particles = remember { 12 }
    val particleValues = remember {
        List(particles) { index ->
            Animatable(initialValue = 0f)
        }
    }

    val rotationAnim = rememberInfiniteTransition()
    val rotationState = rotationAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val scaleAnim = rememberInfiniteTransition()
    val scaleState = scaleAnim.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(Unit) {
        particleValues.forEachIndexed { index, animatable ->
            kotlinx.coroutines.delay(index * 50L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    Canvas(
        modifier = modifier
            .size(120.dp)
            .padding(8.dp)
    ) {
        rotate(rotationState.value) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 3 * scaleState.value
            
            particleValues.forEachIndexed { index, animatable ->
                val angle = (360f / particles) * index
                val x = center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
                val y = center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
                
                // Draw glowing particle
                drawCircle(
                    color = particleColor.copy(alpha = 0.2f * animatable.value),
                    radius = 12f * scaleState.value,
                    center = Offset(x, y)
                )
                
                // Draw core particle
                drawCircle(
                    color = particleColor.copy(alpha = animatable.value),
                    radius = 6f * scaleState.value,
                    center = Offset(x, y)
                )
            }
        }
    }
}

private fun DrawScope.drawParticle(
    center: Offset,
    color: Color,
    alpha: Float
) {
    drawLine(
        color = color.copy(alpha = alpha),
        start = center,
        end = center.copy(
            x = center.x + 8f,
            y = center.y
        ),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
} 