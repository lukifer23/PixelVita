package com.example.androiddiffusion.config

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Process
import com.example.androiddiffusion.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ComponentCallbacks2 {
    companion object {
        private const val TAG = "MemoryManager"
        const val DEFAULT_MIN_MEMORY = 2048L
        const val DEFAULT_MAX_MEMORY = 8192L
        const val DEFAULT_TARGET_MEMORY = 4096L
        private const val MEMORY_THRESHOLD_MODERATE = 0.75f
        private const val MEMORY_THRESHOLD_LOW = 0.85f
        private const val MEMORY_THRESHOLD_CRITICAL = 0.95f
    }
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var _customMemoryLimit: Long = DEFAULT_TARGET_MEMORY
    private var _isLowMemoryMode: Boolean = false
    private var lowMemoryThreshold = 0.2f
    private var criticalMemoryThreshold = 0.1f
    private var targetMemoryUtilization = 0.7f
    private var aggressiveGC = false
    
    var customMemoryLimit: Long
        get() = _customMemoryLimit
        set(value) {
            _customMemoryLimit = value.coerceIn(DEFAULT_MIN_MEMORY, DEFAULT_MAX_MEMORY)
            Logger.d(TAG, "Memory limit set to ${_customMemoryLimit}MB")
        }
    
    var isLowMemoryMode: Boolean
        get() = _isLowMemoryMode
        set(value) {
            _isLowMemoryMode = value
            Logger.d(TAG, "Low memory mode: $value")
        }

    fun startMonitoring() {
        Logger.d(TAG, "Starting memory monitoring")
        updateMemoryInfo()
        logMemoryStatus()
    }

    fun handleTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Logger.d(TAG, "Moderate trim memory signal received")
                if (getMemoryUsage() > MEMORY_THRESHOLD_MODERATE) {
                    clearNonEssentialMemory()
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Logger.d(TAG, "Low memory signal received")
                if (getMemoryUsage() > MEMORY_THRESHOLD_LOW) {
                    clearNonEssentialMemory()
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Logger.d(TAG, "Critical memory signal received")
                if (getMemoryUsage() > MEMORY_THRESHOLD_CRITICAL) {
                    clearAllMemory()
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Logger.d(TAG, "UI hidden trim memory signal received")
                clearNonEssentialMemory()
            }
        }
        updateMemoryInfo()
        logMemoryStatus()
    }

    fun handleLowMemory() {
        Logger.d(TAG, "Low memory warning received")
        clearAllMemory()
        updateMemoryInfo()
        logMemoryStatus()
    }

    private fun clearNonEssentialMemory() {
        Logger.d(TAG, "Clearing non-essential memory")
        System.gc()
    }

    private fun clearAllMemory() {
        Logger.d(TAG, "Clearing all possible memory")
        System.gc()
    }

    fun getTotalMemory(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024) // Convert to MB
    }
    
    fun getAvailableMemory(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024) // Convert to MB
    }
    
    fun getEffectiveMemoryLimit(): Long {
        return if (isLowMemoryMode) {
            (customMemoryLimit * 0.75).toLong()
        } else {
            customMemoryLimit
        }
    }
    
    fun ensureMemoryAvailable(requiredMemoryMB: Long): Boolean {
        val availableMemory = getAvailableMemory()
        val effectiveLimit = getEffectiveMemoryLimit()
        
        Logger.d(TAG, "Memory check - Required: ${requiredMemoryMB}MB, " +
                "Available: ${availableMemory}MB, Effective limit: ${effectiveLimit}MB")
        
        if (availableMemory < requiredMemoryMB) {
            Logger.w(TAG, "Not enough memory available")
            return false
        }
        
        if (requiredMemoryMB > effectiveLimit) {
            Logger.w(TAG, "Required memory exceeds limit")
            return false
        }
        
        return true
    }

    private fun updateMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        Logger.d(TAG, "Memory Info:")
        Logger.d(TAG, "Max Memory: ${maxMemory / 1024 / 1024}MB")
        Logger.d(TAG, "Total Memory: ${totalMemory / 1024 / 1024}MB")
        Logger.d(TAG, "Free Memory: ${freeMemory / 1024 / 1024}MB")
        Logger.d(TAG, "Used Memory: ${usedMemory / 1024 / 1024}MB")
        Logger.d(TAG, "Memory Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
    }

    private fun logMemoryStatus() {
        val memoryUsage = getMemoryUsage()
        Logger.d(TAG, "Memory Status:")
        Logger.d(TAG, "Memory Usage: ${(memoryUsage * 100).toInt()}%")
        Logger.d(TAG, "Memory State: ${getMemoryState(memoryUsage)}")
    }

    private fun getMemoryState(memoryUsage: Float): String {
        return when {
            memoryUsage > MEMORY_THRESHOLD_CRITICAL -> "CRITICAL"
            memoryUsage > MEMORY_THRESHOLD_LOW -> "LOW"
            memoryUsage > MEMORY_THRESHOLD_MODERATE -> "MODERATE"
            else -> "NORMAL"
        }
    }

    private fun getMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory.toFloat() / runtime.maxMemory()
    }

    override fun onTrimMemory(level: Int) {
        handleTrimMemory(level)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Not used
    }

    override fun onLowMemory() {
        handleLowMemory()
    }

    fun initialize() {
        Logger.d(TAG, "Initializing memory manager")
        startMonitoring()
        updateMemoryInfo()
        logMemoryStatus()
    }

    fun forceMemoryLimit(limitMB: Long) {
        customMemoryLimit = limitMB.coerceIn(DEFAULT_MIN_MEMORY, DEFAULT_MAX_MEMORY)
        Logger.d(TAG, "Forced memory limit to ${customMemoryLimit}MB")
        updateMemoryInfo()
        logMemoryStatus()
    }

    fun setLowMemoryThreshold(threshold: Float) {
        lowMemoryThreshold = threshold
    }

    fun setCriticalMemoryThreshold(threshold: Float) {
        criticalMemoryThreshold = threshold
    }

    fun setTargetMemoryUtilization(utilization: Float) {
        targetMemoryUtilization = utilization
    }

    fun enableAggressiveGC(enable: Boolean) {
        aggressiveGC = enable
    }
} 