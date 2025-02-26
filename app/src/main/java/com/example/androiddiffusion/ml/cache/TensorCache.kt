package com.example.androiddiffusion.ml.cache

import ai.onnxruntime.OnnxTensor
import android.util.LruCache
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.NativeMemoryManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TensorCache @Inject constructor(
    private val memoryManager: NativeMemoryManager
) {
    companion object {
        private const val TAG = "TensorCache"
        private const val DEFAULT_CACHE_SIZE = 100 // Number of tensors to cache
        private const val CACHE_MEMORY_LIMIT = 512L * 1024L * 1024L // 512MB
    }
    
    private val cache = object : LruCache<String, CachedTensor>(DEFAULT_CACHE_SIZE) {
        override fun sizeOf(key: String, value: CachedTensor): Int {
            return (value.memorySize / (1024 * 1024)).toInt() // Size in MB
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedTensor,
            newValue: CachedTensor?
        ) {
            if (evicted) {
                oldValue.release()
            }
        }
    }
    
    private var totalMemoryUsed: Long = 0
    
    fun put(key: String, tensor: OnnxTensor, operation: String) {
        try {
            val memorySize = calculateTensorSize(tensor)
            
            if (memorySize > CACHE_MEMORY_LIMIT) {
                Logger.w(TAG, "Tensor too large to cache: $memorySize bytes")
                return
            }
            
            if (totalMemoryUsed + memorySize > CACHE_MEMORY_LIMIT) {
                evictOldest()
            }
            
            val cachedTensor = CachedTensor(
                tensor = tensor,
                memorySize = memorySize,
                operation = operation,
                timestamp = System.currentTimeMillis()
            )
            
            cache.put(key, cachedTensor)
            totalMemoryUsed += memorySize
            
            Logger.d(TAG, "Cached tensor: $key, Size: ${formatBytes(memorySize)}")
        } catch (e: Exception) {
            Logger.e(TAG, "Error caching tensor: $key", e)
        }
    }
    
    fun get(key: String): OnnxTensor? {
        return try {
            cache.get(key)?.tensor?.also {
                Logger.d(TAG, "Cache hit: $key")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error retrieving cached tensor: $key", e)
            null
        }
    }
    
    fun remove(key: String) {
        try {
            cache.remove(key)?.let { cachedTensor ->
                totalMemoryUsed -= cachedTensor.memorySize
                cachedTensor.release()
                Logger.d(TAG, "Removed tensor from cache: $key")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error removing cached tensor: $key", e)
        }
    }
    
    fun clear() {
        try {
            cache.evictAll()
            totalMemoryUsed = 0
            Logger.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Logger.e(TAG, "Error clearing cache", e)
        }
    }
    
    fun getCacheStats(): CacheStats {
        return CacheStats(
            totalItems = cache.size(),
            totalMemoryUsed = totalMemoryUsed,
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount()
        )
    }
    
    private fun evictOldest() {
        val oldest = cache.snapshot().entries
            .minByOrNull { it.value.timestamp }
            ?.key
        
        oldest?.let { remove(it) }
    }
    
    private fun calculateTensorSize(tensor: OnnxTensor): Long {
        val elementSize = when (tensor.info.type) {
            ai.onnxruntime.OnnxJavaType.FLOAT -> 4L
            ai.onnxruntime.OnnxJavaType.INT64 -> 8L
            ai.onnxruntime.OnnxJavaType.INT32 -> 4L
            else -> 4L // Default to 4 bytes
        }
        
        return tensor.info.shape.fold(elementSize) { acc, dim -> acc * dim }
    }
    
    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toFloat()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return String.format("%.2f %s", value, units[unitIndex])
    }
    
    data class CacheStats(
        val totalItems: Int,
        val totalMemoryUsed: Long,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int
    ) {
        val hitRate: Float
            get() = if (hitCount + missCount > 0) {
                hitCount.toFloat() / (hitCount + missCount)
            } else {
                0f
            }
    }
    
    private data class CachedTensor(
        val tensor: OnnxTensor,
        val memorySize: Long,
        val operation: String,
        val timestamp: Long
    ) {
        fun release() {
            try {
                tensor.close()
            } catch (e: Exception) {
                Logger.e(TAG, "Error releasing tensor", e)
            }
        }
    }
}
