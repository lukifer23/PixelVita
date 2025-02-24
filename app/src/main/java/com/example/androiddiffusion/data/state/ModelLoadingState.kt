package com.example.androiddiffusion.data.state

sealed class ModelLoadingState {
    object NotLoaded : ModelLoadingState()
    data class Loading(
        val progress: Int,
        val stage: String = "",
        val details: String = ""
    ) : ModelLoadingState()
    object Loaded : ModelLoadingState()
    data class Error(val error: String) : ModelLoadingState()
} 