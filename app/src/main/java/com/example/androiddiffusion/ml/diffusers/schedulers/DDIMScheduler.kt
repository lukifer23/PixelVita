package com.example.androiddiffusion.ml.diffusers.schedulers

import kotlin.math.sqrt

class DDIMScheduler : Scheduler {
    private var timesteps: IntArray = intArrayOf()
    private var alphasCumprod: FloatArray = floatArrayOf()
    private var initialNoiseStd: Float = 1.0f
    
    override fun setTimesteps(numInferenceSteps: Int) {
        timesteps = IntArray(numInferenceSteps) { it }
        alphasCumprod = generateAlphasCumprod(numInferenceSteps)
        initialNoiseStd = sqrt(1f / (1f - alphasCumprod.last()))
    }
    
    private fun generateAlphasCumprod(numInferenceSteps: Int): FloatArray {
        val betas = FloatArray(numInferenceSteps) { idx ->
            val t = idx.toFloat() / (numInferenceSteps - 1)
            lerp(0.0008f, 0.012f, t)
        }
        
        val alphas = betas.map { 1.0f - it }.toFloatArray()
        val alphasCumprod = FloatArray(numInferenceSteps)
        var cumprod = 1.0f
        for (i in alphas.indices) {
            cumprod *= alphas[i]
            alphasCumprod[i] = cumprod
        }
        return alphasCumprod
    }
    
    override fun step(modelOutput: FloatArray, timestep: Int, sample: FloatArray): FloatArray {
        val stepIndex = timesteps.indexOf(timestep)
        if (stepIndex == -1) {
            throw IllegalArgumentException("Timestep $timestep not found in schedule")
        }

        val alphaProd = alphasCumprod[stepIndex]
        val alphaProdPrev = if (stepIndex > 0) alphasCumprod[stepIndex - 1] else 1f
        
        val sqrtAlphaProd = sqrt(alphaProd)
        val sqrtOneMinusAlphaProd = sqrt(1f - alphaProd)
        val sqrtAlphaProdPrev = sqrt(alphaProdPrev)
        val sqrtOneMinusAlphaProdPrev = sqrt(1f - alphaProdPrev)
        
        for (i in sample.indices) {
            val predOriginalSample = (sample[i] - sqrtOneMinusAlphaProd * modelOutput[i]) / sqrtAlphaProd
            sample[i] = sqrtAlphaProdPrev * predOriginalSample + sqrtOneMinusAlphaProdPrev * modelOutput[i]
        }

        return sample
    }
    
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }
    
    override fun getInitialNoiseStd(): Float = initialNoiseStd

    override fun getTimesteps(): IntArray = timesteps.clone()
}