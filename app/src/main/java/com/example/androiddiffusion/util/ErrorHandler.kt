package com.example.androiddiffusion.util

import android.content.Context
import android.util.Log
import com.example.androiddiffusion.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

object ErrorHandler {
    private const val TAG = "ErrorHandler"
    private val _currentError = MutableStateFlow<AppError?>(null)
    val currentError = _currentError

    fun handleError(context: Context, throwable: Throwable): AppError {
        Log.e(TAG, "Handling error: ${throwable.message}", throwable)
        
        val error = when (throwable) {
            is AppError -> {
                Log.d(TAG, "Processing AppError: ${throwable::class.simpleName}")
                throwable
            }
            is UnknownHostException -> {
                Log.e(TAG, "Network connectivity error", throwable)
                AppError.NetworkError(context.getString(R.string.error_no_internet))
            }
            is IOException -> {
                Log.e(TAG, "I/O or Network error", throwable)
                when {
                    throwable.message?.contains("ENOSPC") == true -> 
                        AppError.StorageError(context.getString(R.string.error_storage))
                    throwable.message?.contains("permission") == true ->
                        AppError.StorageError(context.getString(R.string.error_storage))
                    else -> AppError.NetworkError(context.getString(R.string.error_network))
                }
            }
            is OutOfMemoryError -> {
                Log.e(TAG, "Memory error while loading model", throwable)
                AppError.ResourceError(context.getString(R.string.error_out_of_memory))
            }
            is IllegalStateException -> {
                Log.e(TAG, "Model loading error", throwable)
                AppError.ModelError(context.getString(R.string.error_model_load))
            }
            is SecurityException -> {
                Log.e(TAG, "Security/Permission error", throwable)
                AppError.StorageError(context.getString(R.string.error_storage))
            }
            is DownloadException -> {
                Logger.e(TAG, "Download error: ${throwable.message}")
                AppError.NetworkError(throwable.message ?: context.getString(R.string.error_unknown))
            }
            else -> {
                val message = throwable.message ?: context.getString(R.string.error_unknown)
                Log.e(TAG, "Uncategorized error: $message", throwable)
                
                when {
                    message.contains("download", ignoreCase = true) -> {
                        AppError.NetworkError(context.getString(R.string.error_download))
                    }
                    message.contains("storage", ignoreCase = true) -> {
                        AppError.StorageError(message)
                    }
                    message.contains("model", ignoreCase = true) -> {
                        AppError.ModelError(message)
                    }
                    message.contains("memory", ignoreCase = true) -> {
                        AppError.ResourceError(context.getString(R.string.error_out_of_memory))
                    }
                    else -> {
                        AppError.GenerationError(message)
                    }
                }
            }
        }
        
        Log.d(TAG, "Final error categorization: ${error::class.simpleName} - ${error.message}")
        _currentError.value = error
        return error
    }

    fun clearError() {
        _currentError.value = null
    }
}

// Extension function to handle errors in a consistent way
suspend fun <T> safeApiCall(
    context: Context,
    block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: Exception) {
    Result.failure(ErrorHandler.handleError(context, e))
}

// Extension function for handling errors in Flow
fun <T> Flow<T>.handleErrors(context: Context): Flow<T> = catch { e ->
    ErrorHandler.handleError(context, e)
    throw e
} 