package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class TextTokenizerTest {
    private val context = mock<Context>()
    private val tokenizer = TextTokenizer(context)

    @Test
    fun `tokenize converts words to sequential indices and pads`() {
        val tokens = tokenizer.tokenize("Hello world")
        assertEquals(77, tokens.size)
        assertEquals(0f, tokens[0])
        assertEquals(1f, tokens[1])
        // remaining positions should be padded with zero
        assertEquals(0f, tokens[76])
    }
}
