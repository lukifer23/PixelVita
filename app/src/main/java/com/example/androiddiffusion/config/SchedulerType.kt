package com.example.androiddiffusion.config

/**
 * Represents the scheduler algorithms that can be used during diffusion.
 * Additional schedulers can be added here and selected via [ModelConfig].
 */
enum class SchedulerType {
    DDIM,
    DDPM,
    EULER
}

