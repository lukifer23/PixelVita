package com.example.androiddiffusion.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.app.ActivityManager
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.NativeOptimizer
import java.io.File

data class SystemRequirements(
    val hasEnoughStorage: Boolean,
    val hasEnoughMemory: Boolean,
    val supportsVulkan: Boolean,
    val supportsNNAPI: Boolean,
    val optimalThreadCount: Int,
    val availableStorage: Long,
    val availableMemory: Long,
    val memoryConfig: String,
    val supportsHalfPrecision: Boolean,
    val hasNeuralEngine: Boolean,
    val hasGPU: Boolean,
    val hasDSP: Boolean,
    val totalMemory: Long,
    val cpuCores: Int,
    val isM1orM2: Boolean,
    val supportedABIs: List<String>,
    val vulkanVersion: String,
    val openGLESVersion: String
) {
    companion object {
        private const val TAG = "AndroidDiffusion"
        private const val MIN_STORAGE_BYTES = 2L * 1024 * 1024 * 1024 // 2GB
        private const val MIN_MEMORY_BYTES = 1536L * 1024 * 1024 // 1.5GB for testing
        private const val MIN_MEMORY_MB = 2048L // 2GB minimum memory
        private const val MIN_STORAGE_MB = 1024L // 1GB minimum storage

        fun getSystemRequirements(context: Context): SystemRequirements {
            val pm = context.packageManager
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // Vulkan is always supported since minSdk >= N
            val supportsVulkan = pm.hasSystemFeature("android.hardware.vulkan.version")

            // NNAPI is always supported since minSdk >= P
            val supportsNNAPI = true

            // Get available storage
            val availableStorage = try {
                val statFs = StatFs(context.filesDir.absolutePath)
                statFs.availableBlocksLong * statFs.blockSizeLong / (1024 * 1024) // Convert to MB
            } catch (e: Exception) {
                MIN_STORAGE_MB
            }

            // Get available memory
            val availableMemory = try {
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                memoryInfo.availMem / (1024 * 1024) // Convert to MB
            } catch (e: Exception) {
                MIN_MEMORY_MB
            }

            // Get optimal thread count
            val optimalThreadCount = try {
                val nativeOptimizer = NativeOptimizer.getInstance(context)
                if (nativeOptimizer.isInitialized) {
                    nativeOptimizer.getOptimalThreadCount()
                } else {
                    Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
                }
            } catch (e: Exception) {
                Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            }

            // Check for Neural Engine support - only available on Android 12+ (S)
            val hasNeuralEngine = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    pm.hasSystemFeature("android.hardware.neural_network")
            
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            
            val memoryConfig = "${availableMemory}MB free of ${totalMemoryMB}MB total"
            
            return SystemRequirements(
                hasEnoughStorage = availableStorage >= MIN_STORAGE_MB,
                hasEnoughMemory = availableMemory >= MIN_MEMORY_MB,
                supportsVulkan = supportsVulkan,
                supportsNNAPI = supportsNNAPI,
                optimalThreadCount = optimalThreadCount,
                availableStorage = availableStorage,
                availableMemory = availableMemory,
                memoryConfig = memoryConfig,
                supportsHalfPrecision = true, // Always true since minSdk >= O
                hasNeuralEngine = hasNeuralEngine,
                hasGPU = supportsVulkan,
                hasDSP = pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK),
                totalMemory = totalMemoryMB,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                isM1orM2 = false, // Always false for Android
                supportedABIs = Build.SUPPORTED_ABIS.toList(),
                vulkanVersion = if (supportsVulkan) "1.0" else "None",
                openGLESVersion = "3.0"
            )
        }
    }

    fun meetsMinimumRequirements(): Boolean {
        Logger.d(TAG, "[SystemCheck] Checking minimum requirements:")
        Logger.d(TAG, "[SystemCheck] Storage: Available=${availableStorage}MB, Required=${MIN_STORAGE_MB}MB")
        Logger.d(TAG, "[SystemCheck] Memory: Available=${availableMemory}MB, Required=${MIN_MEMORY_MB}MB")
        Logger.d(TAG, "[SystemCheck] GPU Support: Vulkan=$supportsVulkan, NNAPI=$supportsNNAPI")
        Logger.d(TAG, "[SystemCheck] Memory Details: Total=${totalMemory / 1024 / 1024}MB, Free Memory from config=$memoryConfig")
        
        val hasMinStorage = hasEnoughStorage
        val hasMinMemory = hasEnoughMemory
        val hasGpuSupport = supportsVulkan || supportsNNAPI
        
        Logger.d(TAG, "[SystemCheck] Requirements check results:")
        Logger.d(TAG, "[SystemCheck] - Has minimum storage: $hasMinStorage")
        Logger.d(TAG, "[SystemCheck] - Has minimum memory: $hasMinMemory")
        Logger.d(TAG, "[SystemCheck] - Has GPU support: $hasGpuSupport")
        
        return hasMinStorage && hasMinMemory && hasGpuSupport
    }

    fun getOptimalProfile(): OptimizationProfile {
        return when {
            supportsVulkan && supportsHalfPrecision -> OptimizationProfile.HIGH_PERFORMANCE
            supportsNNAPI -> OptimizationProfile.BALANCED
            else -> OptimizationProfile.POWER_EFFICIENT
        }
    }
}