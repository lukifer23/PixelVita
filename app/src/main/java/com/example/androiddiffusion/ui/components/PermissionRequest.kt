package com.example.androiddiffusion.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.androiddiffusion.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequest(
    permissions: Array<String>,
    rationaleTitle: String = stringResource(R.string.permission_required),
    rationaleText: String = stringResource(R.string.storage_permission_rationale),
    onPermissionResult: (Boolean) -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = permissions.toList()
    ) { permissionsResult ->
        val allGranted = permissionsResult.all { it.value }
        onPermissionResult(allGranted)
    }

    HandlePermissionsRequest(
        permissionsState = permissionsState,
        rationaleTitle = rationaleTitle,
        rationaleText = rationaleText,
        onPermissionResult = onPermissionResult
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun HandlePermissionsRequest(
    permissionsState: MultiplePermissionsState,
    rationaleTitle: String,
    rationaleText: String,
    onPermissionResult: (Boolean) -> Unit
) {
    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(permissionsState) {
        when {
            permissionsState.allPermissionsGranted -> {
                onPermissionResult(true)
            }
            permissionsState.shouldShowRationale -> {
                showRationale = true
            }
            else -> {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { 
                showRationale = false
                onPermissionResult(false)
            },
            title = { Text(rationaleTitle) },
            text = { Text(rationaleText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionsState.launchMultiplePermissionRequest()
                    }
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRationale = false
                        onPermissionResult(false)
                    }
                ) {
                    Text(stringResource(R.string.deny))
                }
            }
        )
    }
} 