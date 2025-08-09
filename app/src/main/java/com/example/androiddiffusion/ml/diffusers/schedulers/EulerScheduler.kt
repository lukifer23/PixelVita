package com.example.androiddiffusion.ml.diffusers.schedulers

/**
 * A very lightweight Euler scheduler that simply applies the model output
 * scaled by a sigma factor for each timestep. This is not a full K-diffusion
 * implementation but serves as a simple alternative scheduler.
 */
class EulerScheduler : Scheduler {
    private var timesteps: IntArray = intArrayOf()
    private var sigmas: FloatArray = floatArrayOf()

    override fun setTimesteps(numInferenceSteps: Int) {
        timesteps = IntArray(numInferenceSteps) { it }
        sigmas = FloatArray(numInferenceSteps) { idx ->
            1f - (idx.toFloat() / numInferenceSteps)
        }
    }

    override fun step(modelOutput: FloatArray, timestep: Int, sample: FloatArray): FloatArray {
        val index = timesteps.indexOf(timestep)
        val sigma = sigmas[index]
        for (i in sample.indices) {
            sample[i] += modelOutput[i] * sigma
        }
        return sample
    }

    override fun getInitialNoiseStd(): Float = sigmas.firstOrNull() ?: 1f

    override fun getTimesteps(): IntArray = timesteps.clone()
}

