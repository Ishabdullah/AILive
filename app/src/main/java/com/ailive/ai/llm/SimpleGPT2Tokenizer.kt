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
    private val bpeRanks = mutableMapOf<Pair<String, String>, Int>()

    private var eosTokenId: Int = 50256

    // Byte encoder for proper GPT-2 BPE
    private val byteEncoder = mutableMapOf<Int, String>()
    private val byteDecoder = mutableMapOf<String, Int>()

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

            // Load BPE merges and build ranks
            Log.d(TAG, "   Loading BPE merges...")
            val mergesArray = modelJson.getJSONArray("merges")
            var mergeCount = 0
            for (i in 0 until mergesArray.length()) {
                val merge = mergesArray.getString(i).split(" ")
                if (merge.size == 2) {
                    val pair = Pair(merge[0], merge[1])
                    bpeMerges.add(pair)
                    bpeRanks[pair] = i  // Rank is the merge order
                    mergeCount++
                }
            }

            Log.i(TAG, "✅ Loaded $mergeCount BPE merges with ranks")

            // Initialize byte encoder (GPT-2 uses a special byte encoding)
            Log.d(TAG, "   Initializing byte encoder...")
            initByteEncoder()
            Log.i(TAG, "✅ Byte encoder initialized (${byteEncoder.size} mappings)")

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
     * Initialize byte encoder (GPT-2's special byte-to-unicode mapping)
     */
    private fun initByteEncoder() {
        // GPT-2 uses a specific byte-to-unicode mapping to handle all possible bytes
        // This is a simplified version that covers printable ASCII + special encoding
        val ranges = listOf(
            33..126,     // Printable ASCII except space
            161..172,    // Latin-1 supplement start
            174..255     // Rest of Latin-1
        )

        val bs = mutableListOf<Int>()
        for (range in ranges) {
            bs.addAll(range.toList())
        }

        var cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }

        for (i in bs.indices) {
            byteEncoder[bs[i]] = cs[i].toChar().toString()
            byteDecoder[cs[i].toChar().toString()] = bs[i]
        }
    }

    /**
     * Get BPE pairs from a word
     */
    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        if (word.size < 2) return pairs

        var prevChar = word[0]
        for (i in 1 until word.size) {
            pairs.add(Pair(prevChar, word[i]))
            prevChar = word[i]
        }
        return pairs
    }

    /**
     * Apply BPE merges to encode a word
     */
    private fun bpe(token: String): String {
        if (token.length == 1) return token

        var word = token.map { it.toString() }
        var pairs = getPairs(word)

        if (pairs.isEmpty()) return token

        while (true) {
            // Find the pair with the lowest merge rank
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break

            // If this pair isn't in our merge rules, we're done
            if (bigram !in bpeRanks) break

            val (first, second) = bigram
            val newWord = mutableListOf<String>()
            var i = 0

            while (i < word.size) {
                val j = word.subList(i, word.size).indexOf(first)
                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }

                newWord.addAll(word.subList(i, i + j))
                i += j

                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }

            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }

        return word.joinToString(" ")
    }

    /**
     * Encode text to token IDs using proper BPE algorithm
     */
    fun encode(text: String): LongArray {
        Log.d(TAG, "🔤 Encoding text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")

        val tokens = mutableListOf<Long>()

        // GPT-2 pattern: splits on whitespace and keeps punctuation
        val pattern = Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")
        val words = pattern.findAll(text).map { it.value }.toList()

        for (word in words) {
            // Convert word to bytes, then to unicode chars using byte encoder
            val tokenBytes = word.toByteArray(Charsets.UTF_8)
            val encodedWord = tokenBytes.map {
                byteEncoder[it.toInt() and 0xFF] ?: ""
            }.joinToString("")

            // Apply BPE to get the final token representation
            val bpeTokens = bpe(encodedWord).split(" ")

            for (bpeToken in bpeTokens) {
                val tokenId = vocab[bpeToken]
                if (tokenId != null) {
                    tokens.add(tokenId.toLong())
                } else {
                    Log.w(TAG, "⚠️  Unknown BPE token: '$bpeToken'")
                }
            }
        }

        Log.d(TAG, "✓ Encoded to ${tokens.size} tokens using proper BPE")
        Log.d(TAG, "   First 10 token IDs: ${tokens.take(10).joinToString()}")

        return tokens.toLongArray()
    }

    /**
     * Decode token IDs to text
     */
    fun decode(ids: LongArray): String {
        Log.d(TAG, "🔠 Decoding ${ids.size} token IDs...")

        // Show first 20 token IDs (LongArray doesn't have take(), so use slice)
        val previewIds = if (ids.size > 20) {
            ids.sliceArray(0 until 20).joinToString()
        } else {
            ids.joinToString()
        }
        Log.d(TAG, "   Token IDs: $previewIds${if (ids.size > 20) "..." else ""}")

        var unknownCount = 0
        val tokens = mutableListOf<String>()

        // LongArray doesn't have mapNotNull, use manual loop
        for (id in ids) {
            val token = reverseVocab[id.toInt()]
            if (token == null) {
                unknownCount++
                Log.w(TAG, "⚠️  Unknown token ID: $id")
            } else {
                tokens.add(token)
            }
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

    /**
     * Clean up resources (tokenizer uses only in-memory structures)
     */
    fun close() {
        Log.d(TAG, "🔒 Closing tokenizer...")
        vocab.clear()
        reverseVocab.clear()
        bpeMerges.clear()
        bpeRanks.clear()
        byteEncoder.clear()
        byteDecoder.clear()
        Log.d(TAG, "✅ Tokenizer closed (all data structures cleared)")
    }
}
