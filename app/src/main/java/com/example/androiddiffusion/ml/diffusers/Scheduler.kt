package com.example.androiddiffusion.ml.diffusers

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

interface Scheduler {
    val timesteps: LongArray
    val initNoiseSigma: Float
    fun step(
        modelOutput: FloatArray,
        timestep: Long,
        sample: FloatArray,
        eta: Float = 0.0f
    ): FloatArray
    fun addNoise(
        originalSample: FloatArray,
        noise: FloatArray,
        timestep: Long
    ): FloatArray
    fun setTimesteps(numInferenceSteps: Int)
}

class DDIMScheduler : Scheduler {
    companion object {
        private const val NUM_TRAIN_TIMESTEPS = 1000
        private const val BETA_START = 0.00085f
        private const val BETA_END = 0.012f
        private const val CLIP_SAMPLE = false
    }

    override var timesteps: LongArray = longArrayOf()
        private set

    override val initNoiseSigma: Float = 1.0f

    private var alphasCumprod: FloatArray = FloatArray(NUM_TRAIN_TIMESTEPS)
    private var finalAlphaCumprod: Float = 1.0f
    private var initialAlphaCumprod: Float = 1.0f

    init {
        // Initialize alpha_t and beta_t parameters
        val betas = generateBetaSchedule()
        var alphasCumProd = 1.0f
        
        for (i in 0 until NUM_TRAIN_TIMESTEPS) {
            val alpha = 1.0f - betas[i]
            alphasCumProd *= alpha
            alphasCumprod[i] = alphasCumProd
        }

        finalAlphaCumprod = alphasCumprod[NUM_TRAIN_TIMESTEPS - 1]
        initialAlphaCumprod = alphasCumprod[0]
    }

    override fun setTimesteps(numInferenceSteps: Int) {
        timesteps = LongArray(numInferenceSteps) { i ->
            ((numInferenceSteps - 1 - i).toLong() * NUM_TRAIN_TIMESTEPS) / numInferenceSteps
        }
    }

    override fun step(
        modelOutput: FloatArray,
        timestep: Long,
        sample: FloatArray,
        eta: Float
    ): FloatArray {
        val stepIndex = timesteps.indexOf(timestep)
        val alphaProdT = getAlphaCumprod(timestep)
        
        // Get previous timestep
        val prevTimestep = if (stepIndex == timesteps.size - 1) {
            0L
        } else {
            timesteps[stepIndex + 1]
        }
        
        val alphaProdTPrev = getAlphaCumprod(prevTimestep)
        
        // Calculate coefficients
        val sqrtAlphaProdT = sqrt(alphaProdT)
        val sqrtOneMinusAlphaProdT = sqrt(1 - alphaProdT)
        val sqrtAlphaProdTPrev = sqrt(alphaProdTPrev)
        val sqrtOneMinusAlphaProdTPrev = sqrt(1 - alphaProdTPrev)
        
        // Calculate predicted original sample
        val predOriginalSample = FloatArray(sample.size) { i ->
            (sample[i] - sqrtOneMinusAlphaProdT * modelOutput[i]) / sqrtAlphaProdT
        }
        
        // Direction pointing to x_t
        val dirXt = FloatArray(modelOutput.size) { i ->
            sqrtOneMinusAlphaProdTPrev * modelOutput[i]
        }
        
        // Random noise
        val noise = if (eta > 0f) {
            FloatArray(sample.size) { (Math.random().toFloat() * 2 - 1) * eta }
        } else {
            FloatArray(sample.size)
        }
        
        // Calculate predicted previous sample mean
        val prevSample = FloatArray(sample.size) { i ->
            sqrtAlphaProdTPrev * predOriginalSample[i] + dirXt[i] + noise[i]
        }
        
        return if (CLIP_SAMPLE) {
            prevSample.map { value -> value.coerceIn(-1f, 1f) }.toFloatArray()
        } else {
            prevSample
        }
    }

    override fun addNoise(
        originalSample: FloatArray,
        noise: FloatArray,
        timestep: Long
    ): FloatArray {
        val alphaCumprod = getAlphaCumprod(timestep)
        val sqrtAlphaCumprod = sqrt(alphaCumprod)
        val sqrtOneMinusAlphaCumprod = sqrt(1 - alphaCumprod)
        
        return FloatArray(originalSample.size) { i ->
            sqrtAlphaCumprod * originalSample[i] + sqrtOneMinusAlphaCumprod * noise[i]
        }
    }

    private fun generateBetaSchedule(): FloatArray {
        val betas = FloatArray(NUM_TRAIN_TIMESTEPS)
        for (i in 0 until NUM_TRAIN_TIMESTEPS) {
            val t = i.toFloat() / (NUM_TRAIN_TIMESTEPS - 1)
            betas[i] = BETA_START + t * (BETA_END - BETA_START)
        }
        return betas
    }

    private fun getAlphaCumprod(timestep: Long): Float {
        return if (timestep >= NUM_TRAIN_TIMESTEPS) {
            finalAlphaCumprod
        } else if (timestep < 0) {
            initialAlphaCumprod
        } else {
            alphasCumprod[timestep.toInt()]
        }
    }
} 