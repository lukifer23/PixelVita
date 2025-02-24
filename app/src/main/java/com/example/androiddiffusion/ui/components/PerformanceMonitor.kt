package com.example.androiddiffusion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.util.PerformanceProfiler
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PerformanceMonitor(
    performanceProfiler: PerformanceProfiler,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    val metrics by performanceProfiler.metrics.collectAsState()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Performance Metrics",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide Details" else "Show Details")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Basic metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(
                    icon = Icons.Default.Timer,
                    label = "Duration",
                    value = "${metrics.totalDuration}ms"
                )
                
                MetricCard(
                    icon = Icons.Default.Memory,
                    label = "Memory",
                    value = formatBytes(metrics.currentMemoryAllocated)
                )
                
                MetricCard(
                    icon = Icons.Default.BarChart,
                    label = "Operations",
                    value = "${metrics.operations.sumOf { it.count }}"
                )
            }
            
            AnimatedVisibility(visible = showDetails) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Operation timeline
                    Text(
                        text = "Operation Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OperationTimeline(
                        operations = metrics.operations,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Detailed metrics
                    LazyColumn {
                        item {
                            Text(
                                text = "Operation Details",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(metrics.operations) { operation ->
                            OperationDetailCard(operation = operation)
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tensor Operations",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(metrics.tensorOperations) { operation ->
                            TensorOperationCard(operation = operation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OperationTimeline(
    operations: List<PerformanceProfiler.OperationSummary>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val totalDuration = operations.maxOfOrNull { it.maxDuration } ?: 0L
        
        // Draw timeline background
        drawRect(
            color = Color.LightGray.copy(alpha = 0.2f),
            size = Size(width, height)
        )
        
        // Draw operation bars
        operations.forEachIndexed { index, operation ->
            val y = (height * index) / operations.size
            val barHeight = height / operations.size * 0.8f
            val barWidth = (width * operation.averageDuration) / totalDuration
            
            drawRect(
                color = primaryColor,
                topLeft = Offset(0f, y + barHeight * 0.1f),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
private fun OperationDetailCard(
    operation: PerformanceProfiler.OperationSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = operation.name,
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Count: ${operation.count}")
                    Text("Avg: ${operation.averageDuration}ms")
                }
                Column {
                    Text("Min: ${operation.minDuration}ms")
                    Text("Max: ${operation.maxDuration}ms")
                }
            }
        }
    }
}

@Composable
private fun TensorOperationCard(
    operation: PerformanceProfiler.TensorOperation,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = operation.type,
                style = MaterialTheme.typography.titleSmall
            )
            Text("Input Shape: ${operation.inputShape}")
            Text("Output Shape: ${operation.outputShape}")
            Text("Duration: ${operation.durationMs}ms")
        }
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