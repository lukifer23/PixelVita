package com.example.androiddiffusion.data.state

import android.graphics.Bitmap

sealed class GenerationState {
    object Idle : GenerationState()
    data class Loading(val progress: Int) : GenerationState()
    data class Complete(val image: Bitmap) : GenerationState()
    data class Error(val message: String) : GenerationState()
} 