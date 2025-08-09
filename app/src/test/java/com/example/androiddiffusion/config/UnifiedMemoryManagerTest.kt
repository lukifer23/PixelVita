package com.example.androiddiffusion.config

import android.app.ActivityManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UnifiedMemoryManagerTest {
    private val activityManager = mock<ActivityManager>()
    private val context = mock<Context> {
        on { getSystemService(Context.ACTIVITY_SERVICE) } doAnswer { activityManager }
    }
    private val manager = UnifiedMemoryManager(context)

    @Test
    fun `allocate and free memory updates native allocation`() {
        doAnswer { invocation ->
            val info = invocation.arguments[0] as ActivityManager.MemoryInfo
            info.availMem = 8L * 1024 * 1024 * 1024
            info.totalMem = 16L * 1024 * 1024 * 1024
            null
        }.whenever(activityManager).getMemoryInfo(any())

        manager.initialize()
        val result = manager.allocateMemory(10, UnifiedMemoryManager.AllocationType.MODEL, "test")
        assertTrue(result.isSuccess)
        var info = manager.getMemoryInfo()
        assertEquals(10, info.nativeAllocatedMB)

        manager.freeMemory("test")
        info = manager.getMemoryInfo()
        assertEquals(0, info.nativeAllocatedMB)
    }
}
