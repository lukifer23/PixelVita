package com.example.androiddiffusion.ml.diffusers

import kotlin.collections.*
import kotlin.text.*
import kotlin.Int
import kotlin.String
import kotlin.FloatArray
import kotlin.IntArray
import kotlin.lazy
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.config.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextTokenizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val VOCAB_FILE = "tokenizer/vocab.json"
        private const val MERGES_FILE = "tokenizer/merges.txt"
        private const val UNK_TOKEN = "<|endoftext|>"
        private const val PAD_TOKEN = "<|endoftext|>"
        private const val COMPONENT = "TextTokenizer"
        private const val MAX_LENGTH = 77
    }

    private val vocab: Map<String, Int> by lazy {
        val json = context.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
        Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
    }

    private val bpeMerges: List<Pair<String, String>> by lazy {
        context.assets.open(MERGES_FILE).bufferedReader().useLines { lines ->
            lines.drop(1) // Skip header
                .map { line ->
                    val (first, second) = line.split(" ")
                    first to second
                }
                .toList()
        }
    }

    fun encode(text: String): IntArray {
        // Basic preprocessing
        var tokens = text.lowercase()
            .replace(Regex("[\\n\\r\\t]+"), " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .flatMap { word ->
                // Split into basic units (characters)
                word.toCharArray().map { it.toString() }
            }
            .toMutableList()

        // Apply BPE merges
        var changed = true
        while (changed) {
            changed = false
            for ((first, second) in bpeMerges) {
                var i = 0
                while (i < tokens.size - 1) {
                    if (tokens[i] == first && tokens[i + 1] == second) {
                        tokens[i] = "$first$second"
                        tokens.removeAt(i + 1)
                        changed = true
                    }
                    i++
                }
            }
        }

        // Convert tokens to IDs
        val ids = tokens.map { token ->
            vocab[token] ?: vocab[UNK_TOKEN]!!
        }

        // Add BOS and EOS tokens, pad to max length
        val finalIds = mutableListOf<Int>()
        finalIds.add(vocab[UNK_TOKEN]!!) // BOS token
        finalIds.addAll(ids)
        finalIds.add(vocab[UNK_TOKEN]!!) // EOS token

        // Pad or truncate to MAX_LENGTH
        return if (finalIds.size >= MAX_LENGTH) {
            finalIds.take(MAX_LENGTH).toIntArray()
        } else {
            finalIds.toIntArray() + IntArray(MAX_LENGTH - finalIds.size) { vocab[PAD_TOKEN]!! }
        }
    }

    fun tokenize(text: String): FloatArray {
        Logger.d(COMPONENT, "Tokenizing text: $text")
        val tokens = text.trim()
            .lowercase()
            .split(" ")
            .filter { it.isNotEmpty() }
            .mapIndexed { index, token -> index.toFloat() }
            .toFloatArray()
        
        // Pad or truncate to MAX_LENGTH
        return FloatArray(MAX_LENGTH) { index ->
            if (index < tokens.size) tokens[index] else 0f
        }
    }
} 