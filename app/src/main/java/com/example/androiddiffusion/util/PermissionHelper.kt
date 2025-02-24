package com.example.androiddiffusion.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            // Storage permission for Android 13+
            Manifest.permission.READ_MEDIA_IMAGES,
            // Notification permission for Android 13+
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            hasPermission(permission)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
} 