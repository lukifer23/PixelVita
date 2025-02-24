package com.example.androiddiffusion.ui.screens

import kotlin.math.roundToInt

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.R
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.ui.components.GradientCard
import com.example.androiddiffusion.ui.components.GlassCard
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    var customMemoryLimit by remember { 
        mutableStateOf<Float>(viewModel.memoryManager.customMemoryLimit.toFloat())
    }
    var isLowMemoryMode by remember {
        mutableStateOf<Boolean>(viewModel.memoryManager.isLowMemoryMode)
    }
    
    val totalMemory = viewModel.memoryManager.getTotalMemory()
    val availableMemory = viewModel.memoryManager.getAvailableMemory()
    val effectiveLimit = viewModel.memoryManager.getEffectiveMemoryLimit()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Memory Management Card
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.memory_management),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Memory Stats
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.memory_stats),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.total_memory, totalMemory),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.available_memory, availableMemory),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.effective_limit, effectiveLimit),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Memory Limit Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.memory_limit),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.memory_limit_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = customMemoryLimit,
                            onValueChange = { newValue ->
                                try {
                                    if (newValue >= MemoryManager.DEFAULT_MIN_MEMORY &&
                                        newValue <= MemoryManager.DEFAULT_MAX_MEMORY) {
                                        customMemoryLimit = newValue
                                        viewModel.memoryManager.customMemoryLimit = newValue.roundToInt().toLong()
                                    }
                                } catch (e: Exception) {
                                    Logger.e("SettingsScreen", "Error updating memory limit", e)
                                }
                            },
                            valueRange = MemoryManager.DEFAULT_MIN_MEMORY.toFloat()..MemoryManager.DEFAULT_MAX_MEMORY.toFloat(),
                            steps = ((MemoryManager.DEFAULT_MAX_MEMORY - MemoryManager.DEFAULT_MIN_MEMORY) / 512).toInt(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.memory_limit_value, customMemoryLimit.roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Low Memory Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.low_memory_mode),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.low_memory_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLowMemoryMode,
                            onCheckedChange = { 
                                isLowMemoryMode = it
                                viewModel.memoryManager.isLowMemoryMode = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    
                    // Reset Button
                    OutlinedButton(
                        onClick = {
                            customMemoryLimit = MemoryManager.DEFAULT_TARGET_MEMORY.toFloat()
                            isLowMemoryMode = false
                            viewModel.memoryManager.forceMemoryLimit(MemoryManager.DEFAULT_TARGET_MEMORY)
                            viewModel.memoryManager.isLowMemoryMode = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.reset_to_defaults))
                    }
                }
            }
        }
    }
} 