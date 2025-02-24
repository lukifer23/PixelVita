package com.example.androiddiffusion.config

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Process
import com.example.androiddiffusion.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

@Singleton
class UnifiedMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ComponentCallbacks2 {
    companion object {
        private const val TAG = "UnifiedMemoryManager"
        const val DEFAULT_MIN_MEMORY_MB = 2048L
        const val DEFAULT_MAX_MEMORY_MB = 8192L
        const val DEFAULT_TARGET_MEMORY_MB = 4096L
        private const val MEMORY_THRESHOLD_MODERATE = 0.75f
        private const val MEMORY_THRESHOLD_LOW = 0.85f
        private const val MEMORY_THRESHOLD_CRITICAL = 0.95f
        private const val GC_INTERVAL_MS = 30000L // 30 seconds
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var lastGCTime = 0L
    private var nativeMemoryAllocations = mutableMapOf<String, MemoryAllocation>()
    
    private val _memoryState = MutableStateFlow<MemoryState>(MemoryState.Normal)
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    private var configuration = MemoryConfiguration(
        minMemoryMB = DEFAULT_MIN_MEMORY_MB,
        maxMemoryMB = DEFAULT_MAX_MEMORY_MB,
        targetMemoryMB = DEFAULT_TARGET_MEMORY_MB,
        lowMemoryThreshold = 0.2f,
        criticalMemoryThreshold = 0.1f,
        targetUtilization = 0.7f,
        isLowMemoryMode = false,
        enableAggressiveGC = false
    )

    sealed class MemoryState {
        object Normal : MemoryState()
        object Low : MemoryState()
        object Critical : MemoryState()
        data class Warning(val message: String) : MemoryState()
    }

    data class MemoryConfiguration(
        val minMemoryMB: Long,
        val maxMemoryMB: Long,
        val targetMemoryMB: Long,
        val lowMemoryThreshold: Float,
        val criticalMemoryThreshold: Float,
        val targetUtilization: Float,
        val isLowMemoryMode: Boolean,
        val enableAggressiveGC: Boolean
    )

    data class MemoryAllocation(
        val id: String,
        val sizeMB: Long,
        val type: AllocationType,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class AllocationType {
        MODEL,
        TENSOR,
        CACHE,
        OTHER
    }

    fun initialize() {
        Logger.d(TAG, "Initializing unified memory manager")
        updateMemoryState()
        logMemoryStatus("Initialization")
    }

    fun allocateMemory(
        sizeMB: Long,
        type: AllocationType,
        id: String = System.currentTimeMillis().toString()
    ): Result<Long> {
        return try {
            if (!canAllocateMemory(sizeMB)) {
                performMemoryMaintenance()
                if (!canAllocateMemory(sizeMB)) {
                    return Result.failure(OutOfMemoryError("Cannot allocate ${sizeMB}MB"))
                }
            }

            val allocation = MemoryAllocation(
                id = id,
                sizeMB = sizeMB,
                type = type
            )
            nativeMemoryAllocations[id] = allocation
            logMemoryStatus("After allocation of ${sizeMB}MB for $type")
            Result.success(allocation.sizeMB)
        } catch (e: Exception) {
            Logger.e(TAG, "Memory allocation failed", e)
            Result.failure(e)
        }
    }

    fun freeMemory(id: String) {
        nativeMemoryAllocations.remove(id)?.let { allocation ->
            try {
                // Free the native memory if it was allocated
                if (allocation.type == AllocationType.MODEL) {
                    freeNativeMemory(allocation.sizeMB)
                }
                logMemoryStatus("After freeing memory for $id (${allocation.type})")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to free memory for $id", e)
            }
        }
    }

    fun updateConfiguration(config: MemoryConfiguration) {
        configuration = config
        Logger.d(TAG, "Memory configuration updated: $config")
        updateMemoryState()
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> handleModerateMemoryTrim()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> handleLowMemoryTrim()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> handleCriticalMemoryTrim()
        }
    }

    override fun onLowMemory() {
        handleCriticalMemoryTrim()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Not used
    }

    private fun canAllocateMemory(requiredMB: Long): Boolean {
        val availableMemory = getAvailableMemoryMB()
        val effectiveLimit = getEffectiveMemoryLimit()
        
        return availableMemory >= requiredMB && 
               (getTotalAllocatedMemory() + requiredMB) <= effectiveLimit
    }

    private fun allocateModelMemory(sizeMB: Long): Long {
        // Implementation for model memory allocation
        return allocateNativeMemory(sizeMB)
    }

    private fun allocateTensorMemory(sizeMB: Long): Long {
        // Implementation for tensor memory allocation
        return allocateNativeMemory(sizeMB)
    }

    private fun allocateCacheMemory(sizeMB: Long): Long {
        // Implementation for cache memory allocation
        return allocateNativeMemory(sizeMB)
    }

    private fun allocateGeneralMemory(sizeMB: Long): Long {
        // Implementation for general memory allocation
        return allocateNativeMemory(sizeMB)
    }

    private fun allocateNativeMemory(sizeMB: Long): Long {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        
        if (sizeMB > maxMemory * 0.75) {
            throw OutOfMemoryError("Requested allocation ($sizeMB MB) exceeds safe memory limit")
        }

        try {
            // Attempt to allocate native memory
            val buffer = ByteBuffer.allocateDirect((sizeMB * 1024 * 1024).toInt())
            if (buffer.capacity() > 0) {
                return buffer.asLongBuffer().get(0)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Native memory allocation failed", e)
        }
        
        return 0L
    }

    private fun freeNativeMemory(address: Long) {
        if (address != 0L) {
            try {
                // Implementation would use JNI to free native memory
                // For now, we just trigger GC to help clean up
                System.gc()
                Runtime.getRuntime().gc()
            } catch (e: Exception) {
                Logger.e(TAG, "Error freeing native memory", e)
            }
        }
    }

    private fun performMemoryMaintenance() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGCTime > GC_INTERVAL_MS) {
            System.gc()
            Runtime.getRuntime().gc()
            Thread.sleep(100)
            lastGCTime = currentTime
        }
    }

    private fun handleModerateMemoryTrim() {
        Logger.d(TAG, "Handling moderate memory trim")
        if (getMemoryUsage() > MEMORY_THRESHOLD_MODERATE) {
            clearNonEssentialMemory()
        }
        updateMemoryState()
    }

    private fun handleLowMemoryTrim() {
        Logger.d(TAG, "Handling low memory trim")
        clearNonEssentialMemory()
        _memoryState.value = MemoryState.Low
        updateMemoryState()
    }

    private fun handleCriticalMemoryTrim() {
        Logger.d(TAG, "Handling critical memory trim")
        clearAllMemory()
        _memoryState.value = MemoryState.Critical
        updateMemoryState()
    }

    private fun clearNonEssentialMemory() {
        // Clear caches and non-essential allocations
        nativeMemoryAllocations.entries
            .filter { (_, allocation) -> allocation.type == AllocationType.CACHE }
            .forEach { (id, _) -> freeMemory(id) }
    }

    private fun clearAllMemory() {
        nativeMemoryAllocations.keys.toList().forEach { freeMemory(it) }
        performMemoryMaintenance()
    }

    private fun updateMemoryState() {
        val memoryUsage = getMemoryUsage()
        _memoryState.value = when {
            memoryUsage > MEMORY_THRESHOLD_CRITICAL -> MemoryState.Critical
            memoryUsage > MEMORY_THRESHOLD_LOW -> MemoryState.Low
            else -> MemoryState.Normal
        }
        logMemoryStatus("State update")
    }

    private fun getMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory.toFloat() / runtime.maxMemory()
    }

    fun getAvailableMemoryMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    fun getTotalMemoryMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    private fun getTotalAllocatedMemory(): Long {
        return nativeMemoryAllocations.values.sumOf { it.sizeMB }
    }

    private fun getEffectiveMemoryLimit(): Long {
        return if (configuration.isLowMemoryMode) {
            (configuration.targetMemoryMB * 0.75).toLong()
        } else {
            configuration.targetMemoryMB
        }
    }

    private fun logMemoryStatus(phase: String) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        
        Logger.d(TAG, "=== Memory Status: $phase ===")
        Logger.d(TAG, "Java Heap:")
        Logger.d(TAG, "- Max Memory: ${maxMemory}MB")
        Logger.d(TAG, "- Total Memory: ${totalMemory}MB")
        Logger.d(TAG, "- Free Memory: ${freeMemory}MB")
        Logger.d(TAG, "- Used Memory: ${usedMemory}MB")
        Logger.d(TAG, "- Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
        Logger.d(TAG, "Native Allocations:")
        Logger.d(TAG, "- Total Allocated: ${getTotalAllocatedMemory()}MB")
        Logger.d(TAG, "- Number of Allocations: ${nativeMemoryAllocations.size}")
        Logger.d(TAG, "Current State: ${memoryState.value}")
    }

    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        val nativeAllocated = getTotalAllocatedMemory()

        return MemoryInfo(
            maxMemoryMB = maxMemory,
            totalMemoryMB = totalMemory,
            freeMemoryMB = freeMemory,
            usedMemoryMB = usedMemory,
            nativeAllocatedMB = nativeAllocated,
            memoryState = _memoryState.value
        )
    }

    data class MemoryInfo(
        val maxMemoryMB: Long,
        val totalMemoryMB: Long,
        val freeMemoryMB: Long,
        val usedMemoryMB: Long,
        val nativeAllocatedMB: Long,
        val memoryState: MemoryState
    )
} 