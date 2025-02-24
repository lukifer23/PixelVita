package com.example.androiddiffusion.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddiffusion.R
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.state.ModelLoadingState
import com.example.androiddiffusion.di.ImageManagerEntryPoint
import com.example.androiddiffusion.data.state.GenerationState
import com.example.androiddiffusion.ui.components.*
import com.example.androiddiffusion.util.ImageManager
import com.example.androiddiffusion.util.ImageUtils
import com.example.androiddiffusion.viewmodel.MainViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun GenerateScreen(
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val imageManager = remember {
        EntryPointAccessors.fromApplication(
            context,
            ImageManagerEntryPoint::class.java
        ).imageManager()
    }
    val scope = rememberCoroutineScope()
    val models by viewModel.models.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val modelLoadingState by viewModel.modelLoadingState.collectAsState()
    
    var selectedModel by remember { mutableStateOf(viewModel.getSelectedModel()) }
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("20") }
    var seed by remember { mutableStateOf(Random.nextLong().toString()) }
    var inputImage by remember { mutableStateOf<Bitmap?>(null) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showModelSelection by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val isGenerating = generationState is GenerationState.Loading
    val progress = when (generationState) {
        is GenerationState.Loading -> (generationState as GenerationState.Loading).progress
        else -> 0
    }
    
    val downloadedModels = models.filter { it.isDownloaded }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Image") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            // Model Selection Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Selected Model",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    selectedModel?.let { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    model.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            IconButton(onClick = { showModelSelection = true }) {
                                Icon(Icons.Default.Edit, "Change model")
                            }
                        }
                        
                        when (modelLoadingState) {
                            is ModelLoadingState.NotLoaded -> {
                                Button(
                                    onClick = { viewModel.loadSelectedModel() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Load Model")
                                }
                            }
                            is ModelLoadingState.Loading -> {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Loading model...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is ModelLoadingState.Loaded -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("Model Loaded") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                            is ModelLoadingState.Error -> {
                                Text(
                                    "Error loading model: ${(modelLoadingState as ModelLoadingState.Error).error}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(
                                    onClick = { viewModel.loadSelectedModel() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry Loading")
                                }
                            }
                        }
                    }
                }
            }
            
            // Model Selection Dialog
            if (showModelSelection) {
                AlertDialog(
                    onDismissRequest = { showModelSelection = false },
                    title = { Text("Select Model") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            downloadedModels.forEach { model ->
                                ListItem(
                                    headlineContent = { Text(model.name) },
                                    supportingContent = { Text(model.description) },
                                    leadingContent = if (model.id == selectedModel?.id) {
                                        {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.clickable {
                                        selectedModel = model
                                        viewModel.selectModel(model)
                                        showModelSelection = false
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showModelSelection = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            // Only show generation UI if model is loaded
            AnimatedVisibility(
                visible = modelLoadingState is ModelLoadingState.Loaded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Prompt input
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    )
                    
                    // Negative prompt
                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = { negativePrompt = it },
                        label = { Text("Negative Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    )
                    
                    // Advanced settings
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Advanced Settings",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = { showAdvancedSettings = !showAdvancedSettings }
                                ) {
                                    Icon(
                                        if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showAdvancedSettings) "Hide" else "Show"
                                    )
                                }
                            }
                            
                            AnimatedVisibility(visible = showAdvancedSettings) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = steps,
                                        onValueChange = { steps = it },
                                        label = { Text("Steps") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isGenerating,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    
                                    OutlinedTextField(
                                        value = seed,
                                        onValueChange = { seed = it },
                                        label = { Text("Seed") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isGenerating,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Generate button
                    Button(
                        onClick = {
                            viewModel.generateImage(
                                prompt = prompt,
                                negativePrompt = negativePrompt,
                                steps = steps.toIntOrNull() ?: 20,
                                seed = seed.toLongOrNull() ?: Random.nextLong()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating && prompt.isNotBlank()
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Create, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate")
                        }
                    }
                    
                    // Progress indicator
                    AnimatedVisibility(
                        visible = isGenerating,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Generating: $progress%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
} 