package com.example.androiddiffusion.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PerformanceProfilerTest {
    
    private lateinit var profiler: PerformanceProfiler
    
    @Before
    fun setup() {
        profiler = PerformanceProfiler()
    }
    
    @Test
    fun `test session lifecycle`() = runTest {
        // When
        profiler.startSession()
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(0L, metrics.totalDuration)
        assertTrue(metrics.operations.isEmpty())
        assertTrue(metrics.tensorOperations.isEmpty())
    }
    
    @Test
    fun `test operation tracking`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        profiler.startOperation("test_operation")
        Thread.sleep(100) // Simulate work
        profiler.endOperation("test_operation")
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(1, metrics.operations.size)
        
        val operation = metrics.operations.first()
        assertEquals("test_operation", operation.name)
        assertTrue(operation.totalDuration >= 100)
        assertEquals(1, operation.count)
    }
    
    @Test
    fun `test multiple operations`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        repeat(3) {
            profiler.startOperation("operation_$it")
            Thread.sleep(50)
            profiler.endOperation("operation_$it")
        }
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(3, metrics.operations.size)
        assertTrue(metrics.totalDuration >= 150)
    }
    
    @Test
    fun `test tensor operation recording`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        profiler.recordTensorOperation(
            operationType = "conv2d",
            inputShape = listOf(1L, 3L, 224L, 224L),
            outputShape = listOf(1L, 64L, 112L, 112L),
            durationMs = 100L
        )
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(1, metrics.tensorOperations.size)
        
        val operation = metrics.tensorOperations.first()
        assertEquals("conv2d", operation.type)
        assertEquals(listOf(1L, 3L, 224L, 224L), operation.inputShape)
        assertEquals(listOf(1L, 64L, 112L, 112L), operation.outputShape)
        assertEquals(100L, operation.durationMs)
    }
    
    @Test
    fun `test memory usage tracking`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        profiler.recordMemoryUsage(
            allocated = 1024L * 1024L, // 1MB
            available = 1024L * 1024L * 1024L // 1GB
        )
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(1024L * 1024L, metrics.currentMemoryAllocated)
        assertEquals(1024L * 1024L * 1024L, metrics.availableMemory)
        assertEquals(1024L * 1024L, metrics.peakMemoryUsage)
    }
    
    @Test
    fun `test peak memory tracking`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        profiler.recordMemoryUsage(allocated = 1024L, available = 2048L)
        profiler.recordMemoryUsage(allocated = 2048L, available = 1024L)
        profiler.recordMemoryUsage(allocated = 512L, available = 2560L)
        
        // Then
        val metrics = profiler.metrics.value
        assertEquals(512L, metrics.currentMemoryAllocated)
        assertEquals(2560L, metrics.availableMemory)
        assertEquals(2048L, metrics.peakMemoryUsage)
    }
    
    @Test
    fun `test session summary generation`() = runTest {
        // Given
        profiler.startSession()
        
        // When
        profiler.startOperation("operation1")
        Thread.sleep(50)
        profiler.endOperation("operation1")
        
        profiler.recordTensorOperation(
            operationType = "conv2d",
            inputShape = listOf(1L, 3L, 224L, 224L),
            outputShape = listOf(1L, 64L, 112L, 112L),
            durationMs = 100L
        )
        
        profiler.recordMemoryUsage(
            allocated = 1024L * 1024L,
            available = 1024L * 1024L * 1024L
        )
        
        // Then
        val summary = profiler.getSessionSummary()
        assertTrue(summary.contains("Performance Profile Summary"))
        assertTrue(summary.contains("operation1"))
        assertTrue(summary.contains("conv2d"))
        assertTrue(summary.contains("1.00 MB")) // Allocated memory
    }
} 