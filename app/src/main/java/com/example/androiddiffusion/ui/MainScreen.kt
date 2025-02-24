package com.example.androiddiffusion.ui

// Android imports
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import kotlinx.coroutines.launch

// App imports
import com.example.androiddiffusion.R
import com.example.androiddiffusion.ui.screens.GenerateScreen
import com.example.androiddiffusion.ui.screens.ModelsScreen
import com.example.androiddiffusion.ui.screens.SettingsScreen
import com.example.androiddiffusion.viewmodel.MainViewModel
import com.example.androiddiffusion.ui.components.ErrorDialog
import com.example.androiddiffusion.util.AppError
import com.example.androiddiffusion.util.ErrorHandler
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.data.state.ModelLoadingState

// Material icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Create

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    viewModel: MainViewModel = hiltViewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Models) }
    val currentError by ErrorHandler.currentError.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    currentError?.let { appError ->
        val onRetry: () -> Unit = {
            when (appError) {
                is AppError.NetworkError,
                is AppError.StorageError,
                is AppError.ModelError -> viewModel.clearDownloadStatus()
                else -> { /* No retry action for other errors */ }
            }
        }
        
        ErrorDialog(
            error = appError,
            onDismiss = { ErrorHandler.clearError() },
            onRetry = onRetry
        )
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.List,
                            contentDescription = "Models",
                            tint = if (currentScreen == Screen.Models) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = "Models",
                            color = if (currentScreen == Screen.Models) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    selected = currentScreen == Screen.Models,
                    onClick = { currentScreen = Screen.Models }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Create,
                            contentDescription = "Generate",
                            tint = if (currentScreen == Screen.Generate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = "Generate",
                            color = if (currentScreen == Screen.Generate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    selected = currentScreen == Screen.Generate,
                    onClick = { currentScreen = Screen.Generate }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    when (targetState) {
                        Screen.Generate -> slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                        Screen.Models -> slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                }
            ) { screen ->
                when (screen) {
                    Screen.Models -> ModelsScreen(
                        viewModel = viewModel,
                        onNavigateToGenerate = { currentScreen = Screen.Generate }
                    )
                    Screen.Generate -> GenerateScreen(
                        viewModel = viewModel,
                        windowSizeClass = windowSizeClass,
                        onNavigateBack = { currentScreen = Screen.Models }
                    )
                }
            }
            
            if (showSettings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { showSettings = false }
                )
            }
        }
    }
}

sealed class Screen {
    object Models : Screen()
    object Generate : Screen()
} 