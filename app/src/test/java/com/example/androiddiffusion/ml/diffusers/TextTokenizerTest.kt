package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TextTokenizerTest {
    private lateinit var tokenizer: TextTokenizer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        tokenizer = TextTokenizer(context)
    }

    @Test
    fun `encode known sentence`() {
        val ids = tokenizer.encode("a photo of a cat")
        assertEquals(77, ids.size)
        val expected = intArrayOf(49407, 97, 49406, 112, 104, 49406, 111, 49406, 49406, 49406, 97, 49406, 99, 49406, 49406)
        assertContentEquals(expected, ids.sliceArray(0 until expected.size))
    }

    @Test
    fun `encode second sentence`() {
        val ids = tokenizer.encode("an astronaut riding a horse")
        val expected = intArrayOf(49407, 49406, 49406, 97, 49406, 114, 49406, 97, 49406, 49406, 114, 49406, 49406, 103, 49406)
        assertContentEquals(expected, ids.sliceArray(0 until expected.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode text exceeding max length`() {
        val longText = List(80) { "word" }.joinToString(" ")
        tokenizer.encode(longText)
    }
}
