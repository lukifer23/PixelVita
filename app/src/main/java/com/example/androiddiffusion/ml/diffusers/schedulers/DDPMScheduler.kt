package com.example.androiddiffusion.ml.diffusers.schedulers

import kotlin.math.sqrt

/**
 * Basic DDPM scheduler implementation. The algorithm here is intentionally
 * lightweight and updates the passed in sample buffer in-place.
 */
class DDPMScheduler : Scheduler {
    private var betas: FloatArray = floatArrayOf()
    private var alphasCumprod: FloatArray = floatArrayOf()
    private var timesteps: IntArray = intArrayOf()

    override fun setTimesteps(numInferenceSteps: Int) {
        timesteps = IntArray(numInferenceSteps) { it }
        betas = FloatArray(numInferenceSteps) { idx ->
            val t = idx.toFloat() / (numInferenceSteps - 1)
            lerp(0.0001f, 0.02f, t)
        }

        val alphas = betas.map { 1f - it }.toFloatArray()
        alphasCumprod = FloatArray(numInferenceSteps)
        var prod = 1f
        for (i in alphas.indices) {
            prod *= alphas[i]
            alphasCumprod[i] = prod
        }
    }

    override fun step(modelOutput: FloatArray, timestep: Int, sample: FloatArray): FloatArray {
        val index = timesteps.indexOf(timestep)
        val beta = betas[index]
        val alpha = 1f - beta
        val alphaBar = alphasCumprod[index]
        val sqrtAlpha = sqrt(alpha)
        val sqrtOneMinusAlpha = sqrt(1f - alpha)
        val sqrtOneMinusAlphaBar = sqrt(1f - alphaBar)

        for (i in sample.indices) {
            val predOriginal = (sample[i] - sqrtOneMinusAlphaBar * modelOutput[i]) / sqrt(alphaBar)
            sample[i] = sqrtAlpha * predOriginal + sqrtOneMinusAlpha * modelOutput[i]
        }
        return sample
    }

    override fun getInitialNoiseStd(): Float = 1f

    override fun getTimesteps(): IntArray = timesteps.clone()

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }
}

