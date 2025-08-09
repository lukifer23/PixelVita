package com.example.androiddiffusion.repository

import android.graphics.Bitmap
import com.example.androiddiffusion.ml.diffusers.DiffusersPipeline
import javax.inject.Inject

class DiffusionRepositoryImpl @Inject constructor(
    private val pipeline: DiffusersPipeline
) : DiffusionRepository {
    override suspend fun loadModel(modelPath: String, onProgress: (String, Int) -> Unit) {
        pipeline.loadModel(modelPath, onProgress)
    }

    override suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        numInferenceSteps: Int,
        guidanceScale: Float,
        width: Int,
        height: Int,
        seed: Long,
        onProgress: suspend (Int) -> Unit
    ): Bitmap {
        return pipeline.generateImage(
            prompt = prompt,
            negativePrompt = negativePrompt,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            width = width,
            height = height,
            seed = seed,
            onProgress = onProgress
        )
    }

    override suspend fun imageToImage(
        prompt: String,
        inputImage: Bitmap,
        negativePrompt: String,
        numInferenceSteps: Int,
        guidanceScale: Float,
        strength: Float,
        width: Int,
        height: Int,
        seed: Long,
        onProgress: suspend (Int) -> Unit
    ): Bitmap {
        return pipeline.imageToImage(
            prompt = prompt,
            inputImage = inputImage,
            negativePrompt = negativePrompt,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            strength = strength,
            width = width,
            height = height,
            seed = seed,
            onProgress = onProgress
        )
    }

    override suspend fun inpaint(
        prompt: String,
        inputImage: Bitmap,
        mask: Bitmap,
        negativePrompt: String,
        numInferenceSteps: Int,
        guidanceScale: Float,
        width: Int,
        height: Int,
        seed: Long,
        onProgress: suspend (Int) -> Unit
    ): Bitmap {
        return pipeline.inpaint(
            prompt = prompt,
            inputImage = inputImage,
            mask = mask,
            negativePrompt = negativePrompt,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            width = width,
            height = height,
            seed = seed,
            onProgress = onProgress
        )
    }

    override fun cleanup() {
        pipeline.cleanup()
    }
}
