package com.example.androiddiffusion.util

import android.content.Context
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMemoryManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NativeMemoryManager"
        init {
            try {
                Logger.d(TAG, "Attempting to load native library memory_manager")
                Logger.d(TAG, "Device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                Logger.d(TAG, "Library search path: ${System.getProperty("java.library.path")}")
                System.loadLibrary("memory_manager")
                Logger.d(TAG, "Successfully loaded native library memory_manager")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e(TAG, "Failed to load native library: ${e.message}")
                Logger.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Unexpected error loading native library: ${e.message}")
                Logger.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                throw e
            }
        }
        
        private const val MB = 1024L * 1024L
        private const val GB = MB * 1024L
    }
    
    private var allocatedMemory = mutableMapOf<Long, Long>() // ptr -> size
    
    init {
        Logger.d(TAG, "Initializing NativeMemoryManager")
        Logger.d(TAG, "Available memory: ${getAvailableMemoryMB()}MB")
    }
    
    external fun allocateMemory(size: Long): Long
    external fun freeMemory(ptr: Long, size: Long)
    external fun getAvailableMemory(): Long
    external fun lockMemory(ptr: Long, size: Long): Boolean
    external fun unlockMemory(ptr: Long, size: Long): Boolean
    
    fun allocateBuffer(sizeInMB: Int): Long {
        Logger.d(TAG, "Attempting to allocate ${sizeInMB}MB")
        val size = sizeInMB.toLong() * MB
        val ptr = allocateMemory(size)
        if (ptr != 0L) {
            allocatedMemory[ptr] = size
            Logger.d(TAG, "Successfully allocated ${sizeInMB}MB at $ptr")
            Logger.d(TAG, "Remaining memory: ${getAvailableMemoryMB()}MB")
        } else {
            Logger.e(TAG, "Failed to allocate ${sizeInMB}MB")
            Logger.e(TAG, "Available memory: ${getAvailableMemoryMB()}MB")
        }
        return ptr
    }
    
    fun freeBuffer(ptr: Long) {
        allocatedMemory[ptr]?.let { size ->
            Logger.d(TAG, "Attempting to free ${size / MB}MB at $ptr")
            freeMemory(ptr, size)
            allocatedMemory.remove(ptr)
            Logger.d(TAG, "Successfully freed ${size / MB}MB at $ptr")
            Logger.d(TAG, "Available memory: ${getAvailableMemoryMB()}MB")
        } ?: run {
            Logger.w(TAG, "Attempted to free unknown buffer at $ptr")
        }
    }
    
    fun getAvailableMemoryMB(): Long {
        return getAvailableMemory() / MB
    }
    
    fun cleanup() {
        Logger.d(TAG, "Starting cleanup of ${allocatedMemory.size} buffers")
        allocatedMemory.forEach { (ptr, size) ->
            Logger.d(TAG, "Cleaning up ${size / MB}MB at $ptr")
            freeMemory(ptr, size)
        }
        allocatedMemory.clear()
        Logger.d(TAG, "Cleanup complete. Available memory: ${getAvailableMemoryMB()}MB")
    }
    
    fun lockBuffer(ptr: Long): Boolean {
        return allocatedMemory[ptr]?.let { size ->
            Logger.d(TAG, "Attempting to lock ${size / MB}MB at $ptr")
            val success = lockMemory(ptr, size)
            if (success) {
                Logger.d(TAG, "Successfully locked ${size / MB}MB at $ptr")
            } else {
                Logger.e(TAG, "Failed to lock ${size / MB}MB at $ptr")
            }
            success
        } ?: run {
            Logger.w(TAG, "Attempted to lock unknown buffer at $ptr")
            false
        }
    }
    
    fun unlockBuffer(ptr: Long): Boolean {
        return allocatedMemory[ptr]?.let { size ->
            Logger.d(TAG, "Attempting to unlock ${size / MB}MB at $ptr")
            val success = unlockMemory(ptr, size)
            if (success) {
                Logger.d(TAG, "Successfully unlocked ${size / MB}MB at $ptr")
            } else {
                Logger.e(TAG, "Failed to unlock ${size / MB}MB at $ptr")
            }
            success
        } ?: run {
            Logger.w(TAG, "Attempted to unlock unknown buffer at $ptr")
            false
        }
    }
} 