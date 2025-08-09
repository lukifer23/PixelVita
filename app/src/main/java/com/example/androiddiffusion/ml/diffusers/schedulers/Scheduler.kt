package com.example.androiddiffusion.ml.diffusers.schedulers

/**
 * Common interface for diffusion schedulers. Implementations are responsible for
 * maintaining the latent sample in-place to avoid unnecessary allocations.
 */
interface Scheduler {
    /** Configure scheduler with the number of inference steps. */
    fun setTimesteps(numInferenceSteps: Int)

    /**
     * Perform a scheduler step.
     * @param modelOutput The predicted noise from the model.
     * @param timestep Current timestep.
     * @param sample The latent sample to be modified in-place.
     * @return The updated sample (same reference as [sample]).
     */
    fun step(modelOutput: FloatArray, timestep: Int, sample: FloatArray): FloatArray

    fun getInitialNoiseStd(): Float

    fun getTimesteps(): IntArray
}

