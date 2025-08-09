package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.androiddiffusion.config.UnifiedMemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mockConstruction
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class DiffusersPipelineIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `loadModel loads tiny onnx models`() = runTest {
        val memoryManager = mock<UnifiedMemoryManager> {
            on { memoryState } doAnswer { MutableStateFlow(UnifiedMemoryManager.MemoryState.Normal) }
        }
        whenever(memoryManager.allocateMemory(any(), any(), any())).thenReturn(Result.success(1))
        whenever(memoryManager.getMemoryInfo()).thenReturn(
            UnifiedMemoryManager.MemoryInfo(0,0,0,0,0, UnifiedMemoryManager.MemoryState.Normal)
        )

        val tokenizer = mock<TextTokenizer>()

        mockConstruction(OnnxModel::class.java) { mock, _ ->
            val state = MutableStateFlow<OnnxModel.LoadingState>(OnnxModel.LoadingState.NotLoaded)
            whenever(mock.loadingState).thenReturn(state)
            runBlocking {
                whenever(mock.loadModel(any())).thenAnswer {
                    state.value = OnnxModel.LoadingState.Loaded
                    Unit
                }
            }
            whenever(mock.isSessionInitialized()).thenReturn(true)
        }.use { construction ->
            val pipeline = DiffusersPipeline(context, memoryManager, tokenizer)
            pipeline.loadModel("model") { _, _ -> }
            assertEquals(3, construction.constructed().size)
        }
    }
}
