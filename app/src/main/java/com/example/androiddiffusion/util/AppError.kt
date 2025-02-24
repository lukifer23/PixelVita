package com.example.androiddiffusion.util

sealed class AppError(override val message: String) : Throwable(message) {
    class NetworkError(message: String) : AppError(message)
    class StorageError(message: String) : AppError(message)
    class ModelError(message: String) : AppError(message)
    class GenerationError(message: String) : AppError(message)
    class DatabaseError(message: String) : AppError(message)
    class ResourceError(message: String) : AppError(message)
    class ValidationError(message: String) : AppError(message)
    class SaveError(message: String) : AppError(message)
    class LoadError(message: String) : AppError(message)
    class PermissionError(message: String) : AppError(message)
    
    companion object {
        fun from(e: Exception): AppError = when (e) {
            is OutOfMemoryError -> ResourceError("Out of memory while generating image")
            is IllegalStateException -> ModelError(e.message ?: "Model error")
            else -> GenerationError("Failed to generate image: ${e.message}")
        }
    }
} 