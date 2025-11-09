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
            Log.i(TAG, "📖 Loading tokenizer from assets...")

            val jsonString = context.assets.open("tokenizer.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            }

            val json = JSONObject(jsonString)

            // Load vocabulary
            val modelJson = json.getJSONObject("model")
            val vocabJson = modelJson.getJSONObject("vocab")

            vocabJson.keys().forEach { token ->
                val id = vocabJson.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token
            }

            Log.i(TAG, "✅ Loaded ${vocab.size} tokens")

            // Load BPE merges
            val mergesArray = modelJson.getJSONArray("merges")
            for (i in 0 until mergesArray.length()) {
                val merge = mergesArray.getString(i).split(" ")
                if (merge.size == 2) {
                    bpeMerges.add(Pair(merge[0], merge[1]))
                }
            }

            Log.i(TAG, "✅ Loaded ${bpeMerges.size} BPE merges")

            // Find EOS token ID
            eosTokenId = vocab[EOS_TOKEN] ?: 50256
            Log.i(TAG, "✅ EOS token ID: $eosTokenId")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load tokenizer", e)
            false
        }
    }

    /**
     * Encode text to token IDs
     * Simplified version - just splits on spaces and looks up in vocab
     */
    fun encode(text: String): LongArray {
        val tokens = mutableListOf<Long>()

        // Very simple tokenization - split on whitespace and punctuation
        val words = text.split(Regex("\\s+|(?=[.,!?])"))

        for (word in words) {
            if (word.isEmpty()) continue

            // Try exact match first
            val tokenId = vocab[word]
            if (tokenId != null) {
                tokens.add(tokenId.toLong())
                continue
            }

            // Try lowercase
            val lowerTokenId = vocab[word.lowercase()]
            if (lowerTokenId != null) {
                tokens.add(lowerTokenId.toLong())
                continue
            }

            // Try with leading space (GPT-2 tokenizer adds spaces)
            val spacedTokenId = vocab[" $word"]
            if (spacedTokenId != null) {
                tokens.add(spacedTokenId.toLong())
                continue
            }

            // Split into characters as fallback
            for (char in word) {
                val charTokenId = vocab[char.toString()] ?: vocab[UNK_TOKEN] ?: 0
                tokens.add(charTokenId.toLong())
            }
        }

        Log.d(TAG, "Encoded \"${text.take(50)}\" to ${tokens.size} tokens")
        return tokens.toLongArray()
    }

    /**
     * Decode token IDs to text
     */
    fun decode(ids: LongArray): String {
        val tokens = ids.mapNotNull { id ->
            reverseVocab[id.toInt()]
        }

        val text = tokens.joinToString("")
            .replace("Ġ", " ")  // GPT-2 uses Ġ for spaces
            .replace("Ċ", "\n") // Newlines
            .trim()

        Log.d(TAG, "Decoded ${ids.size} tokens to \"${text.take(50)}...\"")
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
