package com.example.androiddiffusion.util

import android.content.Context
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMemoryManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NativeMemoryManager"
        private const val DEFAULT_POOL_SIZE = 1024L * 1024L * 1024L // 1GB
        
        init {
            try {
                System.loadLibrary("memory_manager")
                Log.i(TAG, "Native memory manager library loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native memory manager library", e)
            }
        }
    }
    
    private var isInitialized = false
    
    fun initialize(poolSize: Long = DEFAULT_POOL_SIZE) {
        if (!isInitialized) {
            try {
                initializeMemoryPool(poolSize)
                isInitialized = true
                Log.i(TAG, "Memory manager initialized with pool size: $poolSize bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize memory manager", e)
                throw RuntimeException("Failed to initialize memory manager", e)
            }
        }
    }
    
    fun allocateMemory(size: Long, tag: String): Long {
        require(size > 0) { "Size must be positive" }
        require(isInitialized) { "Memory manager not initialized" }
        
        val ptr = allocateMemoryNative(size, tag)
        if (ptr == 0L) {
            throw OutOfMemoryError("Failed to allocate $size bytes")
        }
        return ptr
    }
    
    fun freeMemory(ptr: Long): Boolean {
        require(isInitialized) { "Memory manager not initialized" }
        return freeMemoryNative(ptr)
    }
    
    fun freeAllMemory() {
        if (isInitialized) {
            freeAllMemoryNative()
            isInitialized = false
        }
    }
    
    fun getTotalAllocated(): Long {
        require(isInitialized) { "Memory manager not initialized" }
        return getTotalAllocatedNative()
    }
    
    fun getAvailableMemory(): Long {
        require(isInitialized) { "Memory manager not initialized" }
        return getAvailableMemoryNative()
    }
    
    fun getAvailableMemoryMB(): Long {
        return getAvailableMemory() / (1024L * 1024L)
    }
    
    fun defragmentMemory() {
        require(isInitialized) { "Memory manager not initialized" }
        defragmentMemoryNative()
    }
    
    fun getFragmentationRatio(): Float {
        require(isInitialized) { "Memory manager not initialized" }
        return getFragmentationRatioNative()
    }
    
    fun getMemoryStats(): MemoryStats {
        require(isInitialized) { "Memory manager not initialized" }
        return MemoryStats(
            totalAllocated = getTotalAllocated(),
            availableMemory = getAvailableMemory(),
            fragmentationRatio = getFragmentationRatio()
        )
    }
    
    data class MemoryStats(
        val totalAllocated: Long,
        val availableMemory: Long,
        val fragmentationRatio: Float
    )
    
    // Native methods
    private external fun initializeMemoryPool(size: Long)
    private external fun allocateMemoryNative(size: Long, tag: String): Long
    private external fun freeMemoryNative(ptr: Long): Boolean
    private external fun freeAllMemoryNative()
    private external fun getTotalAllocatedNative(): Long
    private external fun getAvailableMemoryNative(): Long
    private external fun defragmentMemoryNative()
    private external fun getFragmentationRatioNative(): Float
} 