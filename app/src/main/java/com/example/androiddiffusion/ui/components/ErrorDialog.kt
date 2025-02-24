package com.example.androiddiffusion.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.androiddiffusion.R
import com.example.androiddiffusion.util.AppError

@Composable
fun ErrorDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (error) {
                    is AppError.NetworkError -> "Network Error"
                    is AppError.StorageError -> "Storage Error"
                    is AppError.ModelError -> "Model Error"
                    is AppError.GenerationError -> "Generation Error"
                    is AppError.DatabaseError -> "Database Error"
                    is AppError.ResourceError -> "Resource Error"
                    is AppError.ValidationError -> "Validation Error"
                    is AppError.SaveError -> "Save Error"
                    is AppError.LoadError -> "Load Error"
                    is AppError.PermissionError -> "Permission Error"
                }
            )
        },
        text = {
            Text(error.message)
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.dialog_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
} 