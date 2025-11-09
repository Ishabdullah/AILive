package com.ailive.ai.llm

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Simple GPT-2 BPE Tokenizer for Android
 *
 * Pure Kotlin implementation that doesn't require native libraries.
 * Reads tokenizer.json from assets and performs BPE encoding/decoding.
 *
 * @author AILive Team
 * @since Phase 7.8
 */
class SimpleGPT2Tokenizer(private val context: Context) {

    companion object {
        private const val TAG = "SimpleGPT2Tokenizer"

        // Special tokens
        private const val EOS_TOKEN = "<|endoftext|>"
        private const val UNK_TOKEN = "<|endoftext|>"
    }

    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()
    private val bpeMerges = mutableListOf<Pair<String, String>>()

    private var eosTokenId: Int = 50256

    /**
     * Initialize tokenizer from tokenizer.json in assets
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "📖 Loading GPT-2 tokenizer from assets...")
            Log.d(TAG, "   Reading tokenizer.json...")

            val startTime = System.currentTimeMillis()

            // Read JSON from assets
            val jsonString = context.assets.open("tokenizer.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            }

            Log.d(TAG, "   Tokenizer JSON size: ${jsonString.length / 1024} KB")

            val json = JSONObject(jsonString)

            // Load vocabulary
            Log.d(TAG, "   Loading vocabulary...")
            val modelJson = json.getJSONObject("model")
            val vocabJson = modelJson.getJSONObject("vocab")

            var tokenCount = 0
            vocabJson.keys().forEach { token ->
                val id = vocabJson.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token
                tokenCount++
            }

            Log.i(TAG, "✅ Loaded $tokenCount vocabulary tokens")

            // Verify vocab size
            if (tokenCount != 50257) {
                Log.w(TAG, "⚠️  Expected 50257 tokens, got $tokenCount")
            }

            // Load BPE merges
            Log.d(TAG, "   Loading BPE merges...")
            val mergesArray = modelJson.getJSONArray("merges")
            var mergeCount = 0
            for (i in 0 until mergesArray.length()) {
                val merge = mergesArray.getString(i).split(" ")
                if (merge.size == 2) {
                    bpeMerges.add(Pair(merge[0], merge[1]))
                    mergeCount++
                }
            }

            Log.i(TAG, "✅ Loaded $mergeCount BPE merges")

            // Find special tokens
            eosTokenId = vocab[EOS_TOKEN] ?: 50256
            Log.i(TAG, "✅ Special tokens:")
            Log.i(TAG, "   EOS token: '$EOS_TOKEN' = $eosTokenId")
            Log.i(TAG, "   UNK token: '$UNK_TOKEN' = ${vocab[UNK_TOKEN]}")

            // Verify some common tokens
            val testTokens = listOf(" ", "the", "a", "Hello", ".")
            Log.d(TAG, "   Sample token IDs:")
            testTokens.forEach { token ->
                val id = vocab[token]
                if (id != null) {
                    Log.d(TAG, "      '$token' = $id")
                }
            }

            val loadTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ Tokenizer initialized successfully in ${loadTime}ms")
            Log.i(TAG, "   Vocab size: $tokenCount")
            Log.i(TAG, "   BPE merges: $mergeCount")
            Log.i(TAG, "   EOS token ID: $eosTokenId")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load tokenizer", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Encode text to token IDs
     * Simplified version - just splits on spaces and looks up in vocab
     */
    fun encode(text: String): LongArray {
        Log.d(TAG, "🔤 Encoding text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")

        val tokens = mutableListOf<Long>()
        var unknownTokenCount = 0

        // Very simple tokenization - split on whitespace and punctuation
        val words = text.split(Regex("\\s+|(?=[.,!?])"))

        for (word in words) {
            if (word.isEmpty()) continue

            var foundToken = false

            // Try exact match first
            val tokenId = vocab[word]
            if (tokenId != null) {
                tokens.add(tokenId.toLong())
                foundToken = true
                continue
            }

            // Try lowercase
            val lowerTokenId = vocab[word.lowercase()]
            if (lowerTokenId != null) {
                tokens.add(lowerTokenId.toLong())
                foundToken = true
                continue
            }

            // Try with leading space (GPT-2 tokenizer adds spaces)
            val spacedTokenId = vocab[" $word"]
            if (spacedTokenId != null) {
                tokens.add(spacedTokenId.toLong())
                foundToken = true
                continue
            }

            // Split into characters as fallback
            if (!foundToken) {
                unknownTokenCount++
                for (char in word) {
                    val charTokenId = vocab[char.toString()] ?: vocab[UNK_TOKEN] ?: 0
                    tokens.add(charTokenId.toLong())
                }
            }
        }

        Log.d(TAG, "✓ Encoded to ${tokens.size} tokens (${unknownTokenCount} unknown words)")
        Log.d(TAG, "   First 10 token IDs: ${tokens.take(10).joinToString()}")

        return tokens.toLongArray()
    }

    /**
     * Decode token IDs to text
     */
    fun decode(ids: LongArray): String {
        Log.d(TAG, "🔠 Decoding ${ids.size} token IDs...")
        Log.d(TAG, "   Token IDs: ${ids.take(20).joinToString()}${if (ids.size > 20) "..." else ""}")

        var unknownCount = 0
        val tokens = ids.mapNotNull { id ->
            val token = reverseVocab[id.toInt()]
            if (token == null) {
                unknownCount++
                Log.w(TAG, "⚠️  Unknown token ID: $id")
            }
            token
        }

        val text = tokens.joinToString("")
            .replace("Ġ", " ")  // GPT-2 uses Ġ for spaces
            .replace("Ċ", "\n") // Newlines
            .trim()

        Log.d(TAG, "✓ Decoded to ${text.length} characters")
        if (unknownCount > 0) {
            Log.w(TAG, "⚠️  $unknownCount unknown token IDs encountered")
        }
        Log.d(TAG, "   Text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")

        return text
    }

    /**
     * Get EOS token ID
     */
    fun getEosTokenId(): Long = eosTokenId.toLong()

    /**
     * Get vocabulary size
     */
    fun getVocabSize(): Int = vocab.size
}
