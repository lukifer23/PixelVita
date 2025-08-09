package com.example.androiddiffusion.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.ModelRepository
import com.example.androiddiffusion.data.state.GenerationState
import com.example.androiddiffusion.data.state.ModelLoadingState
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.repository.DiffusionRepository
import android.graphics.Bitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.androiddiffusion.data.DownloadStatus
import com.example.androiddiffusion.data.manager.ModelDownloadManager

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val diffusionRepository: DiffusionRepository,
    private val modelDownloadManager: ModelDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _memoryManager = MemoryManager(context)
    val memoryManager: MemoryManager get() = _memoryManager

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _modelLoadingState = MutableStateFlow<ModelLoadingState>(ModelLoadingState.NotLoaded)
    val modelLoadingState: StateFlow<ModelLoadingState> = _modelLoadingState.asStateFlow()

    private val _models = MutableStateFlow<List<DiffusionModel>>(emptyList())
    val models: StateFlow<List<DiffusionModel>> = _models.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    private var selectedModel: DiffusionModel? = null

    init {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { models ->
                _models.value = models
            }
        }
    }

    fun downloadModel(model: DiffusionModel) {
        viewModelScope.launch {
            try {
                _downloadStatus.value = _downloadStatus.value + (model.id to DownloadStatus.Downloading(0))
                modelDownloadManager.downloadModel(model) { progress ->
                    _downloadStatus.value = _downloadStatus.value + (model.id to DownloadStatus.Downloading(progress))
                }
                _downloadStatus.value = _downloadStatus.value + (model.id to DownloadStatus.Completed)
                // Refresh models list after successful download
                modelRepository.getAllModels().collect { models ->
                    _models.value = models
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Download failed: ${e.message}", e)
                _downloadStatus.value = _downloadStatus.value + (model.id to DownloadStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun clearDownloadStatus() {
        _downloadStatus.value = emptyMap()
    }

    fun selectModel(model: DiffusionModel) {
        Logger.d(TAG, "Selecting model: ${model.id}")
        selectedModel = model
        // Do not load the model automatically
    }

    fun getSelectedModel(): DiffusionModel? = selectedModel

    fun loadSelectedModel() {
        viewModelScope.launch {
            try {
                val model = selectedModel
                if (model == null) {
                    Logger.e(TAG, "No model selected to load")
                    _modelLoadingState.value = ModelLoadingState.Error("No model selected")
                    return@launch
                }

                Logger.d(TAG, "Loading model: ${model.id}")
                _modelLoadingState.value = ModelLoadingState.Loading(0)

                // Check if model files exist
                if (!model.isDownloaded) {
                    Logger.e(TAG, "Model files not found for ${model.id}")
                    _modelLoadingState.value = ModelLoadingState.Error("Model files not found")
                    return@launch
                }

                // Initialize the pipeline
                try {
                    diffusionRepository.loadModel(
                        modelPath = model.localPath!!,
                        onProgress = { stage, progress ->
                            _modelLoadingState.value = ModelLoadingState.Loading(progress)
                        }
                    )
                    _modelLoadingState.value = ModelLoadingState.Loaded
                    Logger.d(TAG, "Model loaded successfully: ${model.id}")
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to load model: ${e.message}", e)
                    _modelLoadingState.value = ModelLoadingState.Error(e.message ?: "Unknown error")
                    cleanup()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Unexpected error loading model: ${e.message}", e)
                _modelLoadingState.value = ModelLoadingState.Error(e.message ?: "Unexpected error")
                cleanup()
            }
        }
    }

    fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        steps: Int = 20,
        seed: Long = System.currentTimeMillis()
    ) {
        if (_modelLoadingState.value !is ModelLoadingState.Loaded) {
            Logger.e(TAG, "Cannot generate image: Model not loaded")
            _generationState.value = GenerationState.Error("Model not loaded")
            return
        }

        viewModelScope.launch {
            try {
                _generationState.value = GenerationState.Loading(0)
                val image = diffusionRepository.generateImage(
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    numInferenceSteps = steps,
                    seed = seed
                ) { progress ->
                    _generationState.value = GenerationState.Loading(progress)
                }
                _generationState.value = GenerationState.Complete(image)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate image: ${e.message}", e)
                _generationState.value = GenerationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun imageToImage(
        prompt: String,
        inputImage: Bitmap,
        negativePrompt: String = "",
        steps: Int = 20,
        strength: Float = 0.8f,
        seed: Long = System.currentTimeMillis()
    ) {
        if (_modelLoadingState.value !is ModelLoadingState.Loaded) {
            Logger.e(TAG, "Cannot generate image: Model not loaded")
            _generationState.value = GenerationState.Error("Model not loaded")
            return
        }

        viewModelScope.launch {
            try {
                _generationState.value = GenerationState.Loading(0)
                val image = diffusionRepository.imageToImage(
                    prompt = prompt,
                    inputImage = inputImage,
                    negativePrompt = negativePrompt,
                    numInferenceSteps = steps,
                    strength = strength,
                    seed = seed
                ) { progress ->
                    _generationState.value = GenerationState.Loading(progress)
                }
                _generationState.value = GenerationState.Complete(image)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate image: ${e.message}", e)
                _generationState.value = GenerationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun inpaint(
        prompt: String,
        inputImage: Bitmap,
        mask: Bitmap,
        negativePrompt: String = "",
        steps: Int = 20,
        seed: Long = System.currentTimeMillis()
    ) {
        if (_modelLoadingState.value !is ModelLoadingState.Loaded) {
            Logger.e(TAG, "Cannot generate image: Model not loaded")
            _generationState.value = GenerationState.Error("Model not loaded")
            return
        }

        viewModelScope.launch {
            try {
                _generationState.value = GenerationState.Loading(0)
                val image = diffusionRepository.inpaint(
                    prompt = prompt,
                    inputImage = inputImage,
                    mask = mask,
                    negativePrompt = negativePrompt,
                    numInferenceSteps = steps,
                    seed = seed
                ) { progress ->
                    _generationState.value = GenerationState.Loading(progress)
                }
                _generationState.value = GenerationState.Complete(image)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate image: ${e.message}", e)
                _generationState.value = GenerationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cleanup() {
        diffusionRepository.cleanup()
        _modelLoadingState.value = ModelLoadingState.NotLoaded
        selectedModel = null
    }

    fun resetState() {
        _generationState.value = GenerationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
