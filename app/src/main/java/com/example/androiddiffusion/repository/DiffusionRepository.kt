package com.example.androiddiffusion.repository

import android.graphics.Bitmap

interface DiffusionRepository {
    suspend fun loadModel(modelPath: String, onProgress: (String, Int) -> Unit)
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        numInferenceSteps: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        width: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE,
        height: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap
    suspend fun imageToImage(
        prompt: String,
        inputImage: Bitmap,
        negativePrompt: String = "",
        numInferenceSteps: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        strength: Float = 0.8f,
        width: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE,
        height: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap
    suspend fun inpaint(
        prompt: String,
        inputImage: Bitmap,
        mask: Bitmap,
        negativePrompt: String = "",
        numInferenceSteps: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        width: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE,
        height: Int = com.example.androiddiffusion.config.ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap
    fun cleanup()
}
