package com.example.androiddiffusion.util

import android.content.Context
import android.util.Log
import java.io.Closeable

class NativeOptimizer(context: Context) : Closeable {
    private val applicationContext = context.applicationContext
    
    companion object {
        private const val TAG = "NativeOptimizer"
        
        init {
            try {
                System.loadLibrary("native_optimizations")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
        
        @Volatile
        private var instance: NativeOptimizer? = null
        
        fun getInstance(context: Context): NativeOptimizer {
            return instance ?: synchronized(this) {
                instance ?: NativeOptimizer(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val lock = Any()
    
    var isInitialized = false
        private set
    
    init {
        initialize()
    }
    
    private fun initialize() {
        synchronized(lock) {
            if (!isInitialized) {
                try {
                    // Check if device supports NEON
                    val hasNeon = hasNeonSupport()
                    Log.d(TAG, "NEON support: $hasNeon")
                    
                    // Get optimal thread count
                    val threadCount = getOptimalThreadCount()
                    Log.d(TAG, "Optimal thread count: $threadCount")
                    
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize native optimizer: ${e.message}")
                    throw RuntimeException("Native optimizer initialization failed", e)
                }
            }
        }
    }
    
    fun generateOptimizedNoise(size: Int, mean: Float = 0f, std: Float = 1f): FloatArray {
        require(size > 0) { "Size must be positive" }
        
        val output = FloatArray(size)
        try {
            optimizedNoiseGeneration(output, size, mean, std)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate optimized noise: ${e.message}")
            // Fallback to Java implementation
            generateNoiseFallback(output, mean, std)
        }
        return output
    }
    
    fun performOptimizedTensorOperation(input: FloatArray, scale: Float): FloatArray {
        require(input.isNotEmpty()) { "Input array must not be empty" }
        
        val output = FloatArray(input.size)
        try {
            optimizedTensorOperation(input, output, input.size, scale)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform optimized tensor operation: ${e.message}")
            // Fallback to Java implementation
            performTensorOperationFallback(input, output, scale)
        }
        return output
    }
    
    private fun generateNoiseFallback(output: FloatArray, mean: Float, std: Float) {
        for (i in output.indices step 2) {
            val u1 = Math.random().toFloat()
            val u2 = Math.random().toFloat()
            
            val r = std * kotlin.math.sqrt(-2f * kotlin.math.ln(u1))
            val theta = 2f * kotlin.math.PI.toFloat() * u2
            
            output[i] = mean + r * kotlin.math.cos(theta)
            if (i + 1 < output.size) {
                output[i + 1] = mean + r * kotlin.math.sin(theta)
            }
        }
    }
    
    private fun performTensorOperationFallback(input: FloatArray, output: FloatArray, scale: Float) {
        for (i in input.indices) {
            output[i] = input[i] * scale
        }
    }
    
    override fun close() {
        synchronized(lock) {
            if (isInitialized) {
                isInitialized = false
                instance = null
            }
        }
    }
    
    // Native methods
    private external fun hasNeonSupport(): Boolean
    external fun getOptimalThreadCount(): Int
    private external fun optimizedNoiseGeneration(output: FloatArray, size: Int, mean: Float, std: Float)
    private external fun optimizedTensorOperation(input: FloatArray, output: FloatArray, size: Int, scale: Float)
} 