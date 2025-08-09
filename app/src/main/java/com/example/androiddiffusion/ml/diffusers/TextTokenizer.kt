package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.androiddiffusion.util.Logger
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
        private const val BOS_TOKEN = "<|startoftext|>"
        private const val EOS_TOKEN = "<|endoftext|>"
        private const val PAD_TOKEN = EOS_TOKEN
        private const val COMPONENT = "TextTokenizer"
        private const val MAX_LENGTH = 77
    }

    private val vocab: Map<String, Int> by lazy {
        val json = context.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
        Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
    }

    private val bpeRanks: Map<Pair<String, String>, Int> by lazy {
        context.assets.open(MERGES_FILE).bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapIndexed { index, line ->
                    val (first, second) = line.split(" ")
                    (first to second) to index
                }.toMap()
        }
    }

    private val cache = mutableMapOf<String, IntArray>()

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        var prev = word.first()
        for (i in 1 until word.size) {
            val cur = word[i]
            pairs.add(prev to cur)
            prev = cur
        }
        return pairs
    }

    private fun bpe(token: String): List<String> {
        var word = token.toCharArray().map { it.toString() }.toMutableList()
        word.add("</w>")
        var pairs = if (word.size > 1) getPairs(word) else emptySet()
        while (pairs.isNotEmpty()) {
            val bigram = pairs.minBy { bpeRanks[it] ?: Int.MAX_VALUE }
            val rank = bpeRanks[bigram]
            if (rank == null) break

            val first = bigram.first
            val second = bigram.second
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                val j = word.indexOf(first, i)
                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }
                newWord.addAll(word.subList(i, j))
                i = j
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }
            word = newWord
            if (word.size == 1) {
                break
            } else {
                pairs = getPairs(word)
            }
        }
        return word
    }

    fun encode(text: String): IntArray {
        cache[text]?.let { return it.copyOf() }

        val words = text.lowercase()
            .replace(Regex("[\\n\\r\\t]+"), " ")
            .split(" ")
            .filter { it.isNotEmpty() }

        val tokens = words.flatMap { word -> bpe(word) }

        val bos = vocab[BOS_TOKEN]!!
        val eos = vocab[EOS_TOKEN]!!
        val pad = vocab[PAD_TOKEN]!!

        val tokenIds = mutableListOf<Int>()
        tokenIds.add(bos)
        tokens.forEach { tokenIds.add(vocab[it] ?: vocab[UNK_TOKEN]!!) }
        tokenIds.add(eos)

        require(tokenIds.size <= MAX_LENGTH) {
            "Token length ${tokenIds.size} exceeds max $MAX_LENGTH"
        }

        val result = IntArray(MAX_LENGTH) { idx -> if (idx < tokenIds.size) tokenIds[idx] else pad }
        cache[text] = result
        return result.copyOf()
    }

    fun tokenize(text: String): FloatArray {
        Logger.d(COMPONENT, "Tokenizing text: $text")
        val ids = encode(text)
        return FloatArray(ids.size) { ids[it].toFloat() }
    }
} 