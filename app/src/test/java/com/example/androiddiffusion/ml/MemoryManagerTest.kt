package com.example.androiddiffusion.ml

import android.content.Context
import com.example.androiddiffusion.util.NativeMemoryManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class MemoryManagerTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var memoryManager: NativeMemoryManager
    
    @Before
    fun setup() {
        memoryManager = NativeMemoryManager(mockContext)
    }
    
    @Test
    fun `test memory initialization`() {
        // Given
        val poolSize = 1024L * 1024L * 1024L // 1GB
        
        // When
        memoryManager.initialize(poolSize)
        
        // Then
        assertTrue(memoryManager.getAvailableMemory() > 0)
        assertEquals(0, memoryManager.getTotalAllocated())
    }
    
    @Test
    fun `test memory allocation and deallocation`() {
        // Given
        val size = 1024L * 1024L // 1MB
        memoryManager.initialize()
        
        // When
        val ptr = memoryManager.allocateMemory(size, "test")
        
        // Then
        assertNotNull(ptr)
        assertTrue(ptr > 0)
        assertEquals(size, memoryManager.getTotalAllocated())
        
        // When
        val freed = memoryManager.freeMemory(ptr)
        
        // Then
        assertTrue(freed)
        assertEquals(0, memoryManager.getTotalAllocated())
    }
    
    @Test
    fun `test memory cleanup`() {
        // Given
        memoryManager.initialize()
        val allocations = listOf(
            memoryManager.allocateMemory(1024L, "test1"),
            memoryManager.allocateMemory(2048L, "test2"),
            memoryManager.allocateMemory(4096L, "test3")
        )
        
        // When
        memoryManager.freeAllMemory()
        
        // Then
        assertEquals(0, memoryManager.getTotalAllocated())
    }
    
    @Test(expected = IllegalStateException::class)
    fun `test allocation without initialization throws exception`() {
        // When
        memoryManager.allocateMemory(1024L, "test")
    }
    
    @Test
    fun `test memory fragmentation and defragmentation`() {
        // Given
        memoryManager.initialize()
        val ptrs = mutableListOf<Long>()
        
        // When - Create fragmented memory
        repeat(10) {
            ptrs.add(memoryManager.allocateMemory(1024L, "test$it"))
        }
        
        // Free every other allocation to create fragmentation
        for (i in ptrs.indices step 2) {
            memoryManager.freeMemory(ptrs[i])
        }
        
        val fragmentationBefore = memoryManager.getFragmentationRatio()
        
        // When - Defragment
        memoryManager.defragmentMemory()
        
        // Then
        val fragmentationAfter = memoryManager.getFragmentationRatio()
        assertTrue(fragmentationAfter < fragmentationBefore)
    }
    
    @Test
    fun `test memory stats`() {
        // Given
        memoryManager.initialize()
        val size = 1024L * 1024L // 1MB
        
        // When
        val ptr = memoryManager.allocateMemory(size, "test")
        val stats = memoryManager.getMemoryStats()
        
        // Then
        assertEquals(size, stats.totalAllocated)
        assertTrue(stats.availableMemory > 0)
        assertTrue(stats.fragmentationRatio >= 0f)
    }
} 