package com.example.androiddiffusion.ml.cache

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo
import com.example.androiddiffusion.util.NativeMemoryManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class TensorCacheTest {
    
    @Mock
    private lateinit var memoryManager: NativeMemoryManager
    
    @Mock
    private lateinit var mockTensor: OnnxTensor
    
    @Mock
    private lateinit var mockTensorInfo: TensorInfo
    
    private lateinit var tensorCache: TensorCache
    
    @Before
    fun setup() {
        tensorCache = TensorCache(memoryManager)
        
        // Mock tensor info
        whenever(mockTensorInfo.type).thenReturn(OnnxJavaType.FLOAT)
        whenever(mockTensorInfo.shape).thenReturn(longArrayOf(1, 3, 224, 224))
        whenever(mockTensor.info).thenReturn(mockTensorInfo)
    }
    
    @Test
    fun `test tensor caching and retrieval`() {
        // Given
        val key = "test_tensor"
        
        // When
        tensorCache.put(key, mockTensor, "test_operation")
        val retrieved = tensorCache.get(key)
        
        // Then
        assertNotNull(retrieved)
        assertEquals(mockTensor, retrieved)
    }
    
    @Test
    fun `test tensor removal`() {
        // Given
        val key = "test_tensor"
        tensorCache.put(key, mockTensor, "test_operation")
        
        // When
        tensorCache.remove(key)
        val retrieved = tensorCache.get(key)
        
        // Then
        assertNull(retrieved)
        verify(mockTensor).close()
    }
    
    @Test
    fun `test cache stats`() {
        // Given
        val key = "test_tensor"
        
        // When
        tensorCache.put(key, mockTensor, "test_operation")
        val stats = tensorCache.getCacheStats()
        
        // Then
        assertEquals(1, stats.totalItems)
        assertTrue(stats.totalMemoryUsed > 0)
        assertEquals(0, stats.hitCount)
        assertEquals(1, stats.missCount)
        assertEquals(0, stats.evictionCount)
    }
    
    @Test
    fun `test cache hit rate calculation`() {
        // Given
        val key = "test_tensor"
        tensorCache.put(key, mockTensor, "test_operation")
        
        // When - Generate some hits and misses
        repeat(3) { tensorCache.get(key) } // 3 hits
        tensorCache.get("non_existent") // 1 miss
        
        // Then
        val stats = tensorCache.getCacheStats()
        assertEquals(3, stats.hitCount)
        assertEquals(2, stats.missCount) // 1 initial miss + 1 non-existent miss
        assertEquals(0.6f, stats.hitRate) // 3 hits / 5 total accesses
    }
    
    @Test
    fun `test cache eviction on memory limit`() {
        // Given
        // Mock a large tensor
        whenever(mockTensorInfo.shape).thenReturn(longArrayOf(1, 3, 1024, 1024))
        
        // When - Add multiple large tensors to trigger eviction
        repeat(10) { index ->
            tensorCache.put("tensor_$index", mockTensor, "test_operation")
        }
        
        // Then
        val stats = tensorCache.getCacheStats()
        assertTrue(stats.evictionCount > 0)
    }
    
    @Test
    fun `test cache clear`() {
        // Given
        repeat(5) { index ->
            tensorCache.put("tensor_$index", mockTensor, "test_operation")
        }
        
        // When
        tensorCache.clear()
        
        // Then
        val stats = tensorCache.getCacheStats()
        assertEquals(0, stats.totalItems)
        assertEquals(0, stats.totalMemoryUsed)
        verify(mockTensor, times(5)).close()
    }
} 