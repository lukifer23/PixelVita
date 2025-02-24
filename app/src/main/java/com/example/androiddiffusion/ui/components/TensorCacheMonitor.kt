package com.example.androiddiffusion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.ml.cache.TensorCache
import kotlinx.coroutines.delay

@Composable
fun TensorCacheMonitor(
    tensorCache: TensorCache,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    var cacheStats by remember { mutableStateOf<TensorCache.CacheStats?>(null) }
    
    // Update cache stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            cacheStats = tensorCache.getCacheStats()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tensor Cache",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide Details" else "Show Details")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Basic stats
            cacheStats?.let { stats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        icon = Icons.Default.Storage,
                        label = "Items",
                        value = "${stats.totalItems}"
                    )
                    
                    StatCard(
                        icon = Icons.Default.Memory,
                        label = "Memory",
                        value = formatBytes(stats.totalMemoryUsed)
                    )
                    
                    StatCard(
                        icon = Icons.Default.Speed,
                        label = "Hit Rate",
                        value = "${(stats.hitRate * 100).toInt()}%"
                    )
                }
                
                AnimatedVisibility(visible = showDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        DetailRow("Cache Hits", stats.hitCount.toString())
                        DetailRow("Cache Misses", stats.missCount.toString())
                        DetailRow("Evictions", stats.evictionCount.toString())
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { tensorCache.clear() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear Cache")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
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
private fun DetailRow(
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