package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import com.example.androiddiffusion.config.UnifiedMemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.androiddiffusion.ml.diffusers.schedulers.DDIMScheduler
import org.junit.Test
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doAnswer
import kotlin.test.assertFailsWith

class DiffusersPipelineTest {
    private val context = mock<Context>()
    private val memoryManager = mock<UnifiedMemoryManager> {
        on { memoryState } doAnswer { MutableStateFlow(UnifiedMemoryManager.MemoryState.Normal) }
    }
    private val tokenizer = mock<TextTokenizer>()

    @Test
    fun `validateComponents succeeds when all models loaded`() {
        val pipeline = DiffusersPipeline(context, memoryManager, tokenizer)

        val loaded = MutableStateFlow<OnnxModel.LoadingState>(OnnxModel.LoadingState.Loaded)
        val textEncoder = mock<OnnxModel> {
            on { loadingState } doAnswer { loaded }
            on { isSessionInitialized() } doAnswer { true }
        }
        val unet = mock<OnnxModel> {
            on { loadingState } doAnswer { loaded }
            on { isSessionInitialized() } doAnswer { true }
        }
        val vae = mock<OnnxModel> {
            on { loadingState } doAnswer { loaded }
            on { isSessionInitialized() } doAnswer { true }
        }

        DiffusersPipeline::class.java.getDeclaredField("textEncoder").apply { isAccessible = true; set(pipeline, textEncoder) }
        DiffusersPipeline::class.java.getDeclaredField("unet").apply { isAccessible = true; set(pipeline, unet) }
        DiffusersPipeline::class.java.getDeclaredField("vaeDecoder").apply { isAccessible = true; set(pipeline, vae) }
        DiffusersPipeline::class.java.getDeclaredField("scheduler").apply { isAccessible = true; set(pipeline, DDIMScheduler()) }

        val method = DiffusersPipeline::class.java.getDeclaredMethod("validateComponents").apply { isAccessible = true }
        method.invoke(pipeline) // should not throw

        // Now simulate UNet not loaded
        val notLoaded = MutableStateFlow<OnnxModel.LoadingState>(OnnxModel.LoadingState.NotLoaded)
        whenever(unet.loadingState).thenReturn(notLoaded)
        val ex = assertFailsWith<Exception> { method.invoke(pipeline) }
        assertTrue(ex.cause is IllegalStateException)
    }
}
