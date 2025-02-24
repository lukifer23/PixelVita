package com.example.androiddiffusion.util

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceProfiler @Inject constructor() {
    companion object {
        private const val TAG = "PerformanceProfiler"
    }
    
    private val _metrics = MutableStateFlow<PerformanceMetrics>(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()
    
    private val operations = mutableMapOf<String, OperationMetrics>()
    private var sessionStartTime = 0L
    
    fun startSession() {
        sessionStartTime = SystemClock.elapsedRealtime()
        operations.clear()
        _metrics.value = PerformanceMetrics()
        Log.i(TAG, "Performance profiling session started")
    }
    
    fun startOperation(name: String) {
        val startTime = SystemClock.elapsedRealtime()
        operations[name] = OperationMetrics(name, startTime)
        Log.d(TAG, "Operation started: $name")
    }
    
    fun endOperation(name: String) {
        val endTime = SystemClock.elapsedRealtime()
        operations[name]?.let { metrics ->
            val duration = endTime - metrics.startTime
            metrics.addDuration(duration)
            
            updateMetrics()
            Log.d(TAG, "Operation completed: $name, Duration: ${duration}ms")
        } ?: Log.w(TAG, "Attempted to end unknown operation: $name")
    }
    
    fun recordMemoryUsage(allocated: Long, available: Long) {
        _metrics.value = _metrics.value.copy(
            currentMemoryAllocated = allocated,
            availableMemory = available,
            peakMemoryUsage = maxOf(_metrics.value.peakMemoryUsage, allocated)
        )
    }
    
    fun recordTensorOperation(
        operationType: String,
        inputShape: List<Long>,
        outputShape: List<Long>,
        durationMs: Long
    ) {
        val operation = TensorOperation(
            type = operationType,
            inputShape = inputShape,
            outputShape = outputShape,
            durationMs = durationMs
        )
        
        _metrics.value = _metrics.value.copy(
            tensorOperations = _metrics.value.tensorOperations + operation
        )
        
        Log.d(TAG, "Recorded tensor operation: $operation")
    }
    
    private fun updateMetrics() {
        val currentTime = SystemClock.elapsedRealtime()
        val sessionDuration = currentTime - sessionStartTime
        
        _metrics.value = _metrics.value.copy(
            totalDuration = sessionDuration,
            operations = operations.values.map { it.toOperationSummary() }
        )
    }
    
    fun getSessionSummary(): String {
        val metrics = _metrics.value
        return buildString {
            appendLine("Performance Profile Summary")
            appendLine("-------------------------")
            appendLine("Total Duration: ${metrics.totalDuration}ms")
            appendLine("Peak Memory Usage: ${formatBytes(metrics.peakMemoryUsage)}")
            appendLine("Current Memory Allocated: ${formatBytes(metrics.currentMemoryAllocated)}")
            appendLine("Available Memory: ${formatBytes(metrics.availableMemory)}")
            appendLine("\nOperation Breakdown:")
            metrics.operations.forEach { op ->
                appendLine("  ${op.name}:")
                appendLine("    Count: ${op.count}")
                appendLine("    Avg Duration: ${op.averageDuration}ms")
                appendLine("    Min Duration: ${op.minDuration}ms")
                appendLine("    Max Duration: ${op.maxDuration}ms")
            }
            appendLine("\nTensor Operations:")
            metrics.tensorOperations.forEach { op ->
                appendLine("  ${op.type}:")
                appendLine("    Input Shape: ${op.inputShape}")
                appendLine("    Output Shape: ${op.outputShape}")
                appendLine("    Duration: ${op.durationMs}ms")
            }
        }
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
    
    data class PerformanceMetrics(
        val totalDuration: Long = 0,
        val operations: List<OperationSummary> = emptyList(),
        val tensorOperations: List<TensorOperation> = emptyList(),
        val currentMemoryAllocated: Long = 0,
        val availableMemory: Long = 0,
        val peakMemoryUsage: Long = 0
    )
    
    data class OperationSummary(
        val name: String,
        val count: Int,
        val totalDuration: Long,
        val minDuration: Long,
        val maxDuration: Long
    ) {
        val averageDuration: Long
            get() = if (count > 0) totalDuration / count else 0
    }
    
    private class OperationMetrics(
        val name: String,
        val startTime: Long,
        var count: Int = 0,
        var totalDuration: Long = 0,
        var minDuration: Long = Long.MAX_VALUE,
        var maxDuration: Long = 0
    ) {
        fun addDuration(duration: Long) {
            count++
            totalDuration += duration
            minDuration = minOf(minDuration, duration)
            maxDuration = maxOf(maxDuration, duration)
        }
        
        fun toOperationSummary() = OperationSummary(
            name = name,
            count = count,
            totalDuration = totalDuration,
            minDuration = minDuration,
            maxDuration = maxDuration
        )
    }
    
    data class TensorOperation(
        val type: String,
        val inputShape: List<Long>,
        val outputShape: List<Long>,
        val durationMs: Long
    )
} 