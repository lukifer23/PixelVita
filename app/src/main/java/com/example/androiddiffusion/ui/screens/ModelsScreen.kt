package com.example.androiddiffusion.ui.screens

import kotlin.collections.*
import kotlin.coroutines.*
import kotlin.String
import kotlin.Unit
import kotlin.Int
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.R
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.config.QuantizationType
import com.example.androiddiffusion.data.DownloadStatus
import com.example.androiddiffusion.data.state.ModelLoadingState
import com.example.androiddiffusion.ui.components.ModelListItem
import com.example.androiddiffusion.viewmodel.MainViewModel
import com.example.androiddiffusion.util.AppError
import java.text.DecimalFormat
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: MainViewModel,
    onNavigateToGenerate: () -> Unit
) {
    val models by viewModel.models.collectAsState()
    val preInstalledModel = models.find { it.id == "sd35m" }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.2
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    preInstalledModel?.let { model ->
                        viewModel.selectModel(model)
                        onNavigateToGenerate()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { 
                    Icon(
                        Icons.Rounded.Create,
                        "Generate image",
                        modifier = Modifier.size(24.dp)
                    )
                },
                text = { 
                    Text(
                        "Generate",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (models.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Loading models...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    preInstalledModel?.let { model ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("Pre-installed") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                    
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(formatFileSize(model.size)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Storage,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                                
                                Button(
                                    onClick = {
                                        viewModel.selectModel(model)
                                        onNavigateToGenerate()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.Create,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Generating")
                                }
                            }
                        }
                        
                        Text(
                            text = "Additional models can be selected in the generation screen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCard(
    model: DiffusionModel,
    downloadProgress: Float?,
    onDownload: (DiffusionModel) -> Unit,
    onDelete: (DiffusionModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var showQuantizationInfo by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (model.isDownloaded) {
                    IconButton(onClick = { onDelete(model) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onDownload(model) },
                        enabled = downloadProgress == null
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.download),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            downloadProgress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress.toFloat() / 100f,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(model.type.name) },
                    enabled = false
                )
                
                Text(
                    text = formatFileSize(model.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text("v${model.version}") },
                        enabled = false
                    )
                    
                    model.quantization?.let { _ ->
                        IconButton(
                            onClick = { showQuantizationInfo = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Quantization Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            if (showQuantizationInfo) {
                model.quantization?.let { quantInfo ->
                    AlertDialog(
                        onDismissRequest = { showQuantizationInfo = false },
                        title = { Text("Model Optimization Info") },
                        text = {
                            Column {
                                Text(
                                    text = "Type: ${formatQuantizationType(quantInfo.type)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Size: ${formatFileSize(model.size)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (quantInfo.originalSize > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Size reduction: ${calculateSizeReduction(model.size, quantInfo.originalSize)}%",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "This model has been optimized for mobile devices to reduce size and improve performance while maintaining quality.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showQuantizationInfo = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun formatQuantizationType(type: QuantizationType): String {
    return when (type) {
        QuantizationType.NONE -> "No quantization (FP32)"
        QuantizationType.INT8 -> "8-bit integer"
        QuantizationType.INT4 -> "4-bit integer"
        QuantizationType.INT2 -> "2-bit integer"
        QuantizationType.DYNAMIC -> "Dynamic quantization"
    }
}

private fun calculateSizeReduction(currentSize: Long, originalSize: Long): Int {
    return ((originalSize - currentSize).toFloat() / originalSize * 100).toInt()
}

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        size >= 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        size >= 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))} MB"
        size >= 1024 -> "${df.format(size / 1024.0)} KB"
        else -> "$size B"
    }
}

@Composable
fun ErrorCard(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Retry")
            }
        }
    }
} 