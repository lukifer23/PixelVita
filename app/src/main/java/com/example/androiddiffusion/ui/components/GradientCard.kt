package com.example.androiddiffusion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    ),
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        )
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(colors)
            )
            .padding(1.dp)  // Border width
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp)),  // Slightly smaller for border effect
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            content = content
        )
    }
}

@Composable
fun ShimmerGradient(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color.White.copy(alpha = 0.2f),
        Color.White.copy(alpha = 0.4f),
        Color.White.copy(alpha = 0.2f)
    )
) {
    val transition = rememberInfiniteTransition()
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = colors,
                    startX = translateAnimation.value - 500f,
                    endX = translateAnimation.value + 500f
                )
            )
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            content()
        }
    }
} 