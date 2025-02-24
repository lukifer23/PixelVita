package com.example.androiddiffusion.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.state.GenerationState
import com.example.androiddiffusion.ml.diffusers.DiffusersPipeline
import com.example.androiddiffusion.util.AppError
import com.example.androiddiffusion.util.ImageSaver
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.NotificationManager
import com.example.androiddiffusion.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val diffusersPipeline: DiffusersPipeline,
    private val imageSaver: ImageSaver,
    private val permissionHelper: PermissionHelper,
    private val notificationManager: NotificationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _currentImage = MutableStateFlow<Bitmap?>(null)
    val currentImage: StateFlow<Bitmap?> = _currentImage.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _savedImageUri = MutableStateFlow<Uri?>(null)
    val savedImageUri: StateFlow<Uri?> = _savedImageUri.asStateFlow()

    private val _requiredPermissions = MutableStateFlow<Array<String>?>(null)
    val requiredPermissions: StateFlow<Array<String>?> = _requiredPermissions.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    fun generateImage(
        model: DiffusionModel,
        prompt: String,
        negativePrompt: String,
        steps: Int = ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        width: Int = ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE,
        height: Int = ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT,
        seed: Long = Random.nextLong(),
        inputImage: Bitmap? = null
    ) {
        if (_generationState.value is GenerationState.Loading) {
            return
        }

        viewModelScope.launch {
            try {
                _generationState.value = GenerationState.Loading(0)
                _error.value = null
                _progress.value = 0
                _savedImageUri.value = null
                _successMessage.value = null

                val result = diffusersPipeline.generateImage(
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    numInferenceSteps = steps,
                    guidanceScale = guidanceScale,
                    width = width,
                    height = height,
                    seed = seed,
                    onProgress = { progress ->
                        _progress.value = progress
                        _generationState.value = GenerationState.Loading(progress)
                    }
                )

                _currentImage.value = result
                result?.let { bitmap ->
                    _generationState.value = GenerationState.Complete(bitmap)
                    onImageGenerationComplete()
                } ?: run {
                    _error.value = AppError.GenerationError("Failed to generate image")
                    _generationState.value = GenerationState.Error("Failed to generate image")
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error occurred"
                _error.value = AppError.GenerationError(errorMessage)
                _generationState.value = GenerationState.Error(errorMessage)
            }
        }
    }

    fun saveCurrentImage() {
        val image = _currentImage.value ?: return
        
        if (!permissionHelper.hasRequiredPermissions()) {
            _requiredPermissions.value = permissionHelper.getRequiredPermissions()
            return
        }
        
        viewModelScope.launch {
            try {
                val uri = imageSaver.saveImage(image)
                if (uri != null) {
                    _savedImageUri.value = uri
                    onImageSaved()
                } else {
                    _error.value = AppError.SaveError("Failed to save image")
                }
            } catch (e: Exception) {
                _error.value = AppError.SaveError(e.message ?: "Unknown error while saving")
            }
        }
    }

    fun shareCurrentImage() {
        if (!permissionHelper.hasRequiredPermissions()) {
            _requiredPermissions.value = permissionHelper.getRequiredPermissions()
            return
        }

        val uri = _savedImageUri.value ?: run {
            saveCurrentImage()
            return
        }
        
        imageSaver.shareImage(uri)
        _successMessage.value = "Image shared successfully"
    }

    fun onPermissionResult(granted: Boolean) {
        _requiredPermissions.value = null
        if (granted) {
            // Retry the last operation
            _savedImageUri.value?.let {
                shareCurrentImage()
            } ?: run {
                saveCurrentImage()
            }
        } else {
            _error.value = AppError.StorageError("Storage permission denied")
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun clearCurrentImage() {
        _currentImage.value = null
        _savedImageUri.value = null
        _generationState.value = GenerationState.Idle
        _successMessage.value = null
    }

    fun getRandomSeed(): Long = Random.nextLong()

    private fun onImageGenerationComplete() {
        _successMessage.value = "Image generated successfully"
        notificationManager.showGenerationCompleteNotification()
    }

    private fun onImageSaved() {
        _successMessage.value = "Image saved successfully"
        _savedImageUri.value?.let { uri ->
            notificationManager.showImageSavedNotification(uri)
        }
    }

    fun resetState() {
        _generationState.value = GenerationState.Idle
    }
} 