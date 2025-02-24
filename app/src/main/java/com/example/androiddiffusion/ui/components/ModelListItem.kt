package com.example.androiddiffusion.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.DownloadStatus
import java.text.DecimalFormat

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ModelListItem(
    model: DiffusionModel,
    downloadStatus: DownloadStatus.Downloading?,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoaded: Boolean = false
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isLoaded -> MaterialTheme.colorScheme.primaryContainer
                model.id == "sd35m" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (model.id == "sd35m") {
                            SuggestionChip(
                                onClick = { },
                                label = { 
                                    Text(
                                        "Pre-installed",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        }
                        if (isLoaded) {
                            AssistChip(
                                onClick = { },
                                label = { 
                                    Text(
                                        "Ready",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                
                if (downloadStatus != null) {
                    CircularProgressIndicator(
                        progress = downloadStatus.progress.toFloat() / 100f,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    FilledIconButton(
                        onClick = onDownloadClick,
                        enabled = !model.isDownloaded || !isLoaded,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = when {
                                isLoaded -> MaterialTheme.colorScheme.primary
                                model.isDownloaded -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            imageVector = when {
                                isLoaded -> Icons.Default.CheckCircle
                                model.isDownloaded -> Icons.Default.PlayArrow
                                else -> Icons.Default.Download
                            },
                            contentDescription = when {
                                isLoaded -> "Model Ready"
                                model.isDownloaded -> "Load Model"
                                else -> "Download Model"
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            formatFileSize(model.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Info,
                            "Size",
                            Modifier.size(18.dp)
                        )
                    }
                )
                
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            model.type.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Info,
                            "Type",
                            Modifier.size(18.dp)
                        )
                    }
                )
                
                model.quantization?.let { quantization ->
                    AssistChip(
                        onClick = { },
                        label = { 
                            Text(
                                quantization.type.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Info,
                                "Quantization",
                                Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    val sizeKb = size.toDouble() / 1024
    val sizeMb = sizeKb / 1024
    val sizeGb = sizeMb / 1024
    return when {
        sizeGb >= 1 -> "${df.format(sizeGb)} GB"
        sizeMb >= 1 -> "${df.format(sizeMb)} MB"
        sizeKb >= 1 -> "${df.format(sizeKb)} KB"
        else -> "${size} bytes"
    }
} 