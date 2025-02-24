package com.example.androiddiffusion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.util.NativeMemoryManager
import kotlinx.coroutines.*
import kotlin.math.min

@Composable
fun MemoryMonitor(
    memoryManager: NativeMemoryManager,
    modifier: Modifier = Modifier
) {
    var memoryStats by remember { mutableStateOf<NativeMemoryManager.MemoryStats?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    
    // Update memory stats periodically
    LaunchedEffect(Unit) {
        while (isActive) {
            memoryStats = memoryManager.getMemoryStats()
            delay(1000) // Update every second
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Memory usage graph
            memoryStats?.let { stats ->
                MemoryUsageGraph(
                    totalAllocated = stats.totalAllocated,
                    availableMemory = stats.availableMemory,
                    fragmentationRatio = stats.fragmentationRatio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Memory stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Memory Usage",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide Details" else "Show Details")
                }
            }
            
            if (showDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                
                memoryStats?.let { stats ->
                    MemoryStatsDisplay(stats)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { memoryManager.defragmentMemory() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Defragment Memory")
                }
            }
        }
    }
}

@Composable
private fun MemoryUsageGraph(
    totalAllocated: Long,
    availableMemory: Long,
    fragmentationRatio: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition()
    val animatedProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val outlineColor = MaterialTheme.colorScheme.outline
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val total = totalAllocated + availableMemory
        val allocatedWidth = (width * totalAllocated.toFloat() / total)
        
        // Background
        drawRect(
            color = Color.LightGray.copy(alpha = 0.2f),
            size = Size(width, height)
        )
        
        // Allocated memory
        drawRect(
            color = primaryColor,
            size = Size(allocatedWidth, height)
        )
        
        // Fragmentation indicator
        val fragmentationIndicatorWidth = 2.dp.toPx()
        val fragmentationBlocks = (width * fragmentationRatio).toInt()
        for (i in 0 until fragmentationBlocks) {
            val x = i * (width / fragmentationBlocks)
            drawLine(
                color = Color.Red.copy(alpha = 0.3f * animatedProgress),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = fragmentationIndicatorWidth
            )
        }
        
        // Border
        drawRect(
            color = outlineColor,
            size = Size(width, height),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
private fun MemoryStatsDisplay(stats: NativeMemoryManager.MemoryStats) {
    Column {
        MemoryStatRow(
            label = "Total Allocated",
            value = formatBytes(stats.totalAllocated)
        )
        MemoryStatRow(
            label = "Available Memory",
            value = formatBytes(stats.availableMemory)
        )
        MemoryStatRow(
            label = "Fragmentation",
            value = String.format("%.2f blocks/KB", stats.fragmentationRatio)
        )
    }
}

@Composable
private fun MemoryStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toFloat()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
} 