package com.example.androiddiffusion.config

import android.util.Log

object NativeOptimizations {
    private const val TAG = "NativeOptimizations"
    private var isInitialized = false

    init {
        try {
            System.loadLibrary("native_optimizations")
            isInitialized = true
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            isInitialized = false
        }
    }

    fun hasGPUSupport(): Boolean {
        return try {
            if (!isInitialized) {
                Log.w(TAG, "Native library not initialized")
                return false
            }
            isDeviceSupported()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking GPU support: ${e.message}")
            false
        }
    }

    fun getOptimalNumThreads(): Int {
        return if (isInitialized) {
            try {
                getOptimalNumThreadsNative()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get optimal thread count: ${e.message}")
                Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            }
        } else {
            Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        }
    }

    private external fun isDeviceSupported(): Boolean
    private external fun getOptimalNumThreadsNative(): Int
} 