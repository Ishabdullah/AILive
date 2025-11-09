package com.ailive.ai.llm

import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Qwen2-VL BPE Tokenizer for Android
 *
 * Pure Kotlin implementation for Qwen2-VL-2B-Instruct model.
 * Reads vocab.json and merges.txt from Downloads folder.
 *
 * @author AILive Team
 * @since Phase 8.0
 */
class QwenVLTokenizer {

    companion object {
        private const val TAG = "QwenVLTokenizer"

        // Qwen special tokens
        private const val EOS_TOKEN = "<|endoftext|>"
        private const val IM_START_TOKEN = "<|im_start|>"
        private const val IM_END_TOKEN = "<|im_end|>"
        private const val VISION_START_TOKEN = "<|vision_start|>"
        private const val VISION_END_TOKEN = "<|vision_end|>"
    }

    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()
    private val bpeMerges = mutableListOf<Pair<String, String>>()
    private val bpeRanks = mutableMapOf<Pair<String, String>, Int>()

    private var eosTokenId: Int = 151643  // Qwen's EOS token
    private var imStartTokenId: Int = 151644
    private var imEndTokenId: Int = 151645

    // Byte encoder for proper BPE
    private val byteEncoder = mutableMapOf<Int, String>()
    private val byteDecoder = mutableMapOf<String, Int>()

    /**
     * Initialize tokenizer from vocab.json and merges.txt in models folder
     *
     * @param modelsDir Directory where model files are stored (null = use public Downloads)
     */
    fun initialize(modelsDir: File? = null): Boolean {
        return try {
            val downloadsDir = modelsDir ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            Log.i(TAG, "📖 Loading Qwen2-VL tokenizer from ${downloadsDir.absolutePath}...")

            val startTime = System.currentTimeMillis()

            // Load vocabulary from vocab.json
            Log.d(TAG, "   Reading vocab.json...")
            val vocabFile = File(downloadsDir, "vocab.json")
            if (!vocabFile.exists()) {
                Log.e(TAG, "❌ vocab.json not found in Downloads!")
                return false
            }

            val vocabString = vocabFile.readText()
            Log.d(TAG, "   Vocab JSON size: ${vocabString.length / 1024} KB")

            val vocabJson = JSONObject(vocabString)
            var tokenCount = 0
            vocabJson.keys().forEach { token ->
                val id = vocabJson.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token
                tokenCount++
            }

            Log.i(TAG, "✅ Loaded $tokenCount vocabulary tokens")

            // Load BPE merges from merges.txt
            Log.d(TAG, "   Reading merges.txt...")
            val mergesFile = File(downloadsDir, "merges.txt")
            if (!mergesFile.exists()) {
                Log.e(TAG, "❌ merges.txt not found in Downloads!")
                return false
            }

            var mergeCount = 0
            BufferedReader(FileReader(mergesFile)).use { reader ->
                reader.lineSequence()
                    .drop(1)  // Skip header line
                    .forEach { line ->
                        val parts = line.trim().split(" ")
                        if (parts.size == 2) {
                            val pair = Pair(parts[0], parts[1])
                            bpeMerges.add(pair)
                            bpeRanks[pair] = mergeCount
                            mergeCount++
                        }
                    }
            }

            Log.i(TAG, "✅ Loaded $mergeCount BPE merges with ranks")

            // Initialize byte encoder
            Log.d(TAG, "   Initializing byte encoder...")
            initByteEncoder()
            Log.i(TAG, "✅ Byte encoder initialized (${byteEncoder.size} mappings)")

            // Find special tokens
            eosTokenId = vocab[EOS_TOKEN] ?: 151643
            imStartTokenId = vocab[IM_START_TOKEN] ?: 151644
            imEndTokenId = vocab[IM_END_TOKEN] ?: 151645

            Log.i(TAG, "✅ Special tokens:")
            Log.i(TAG, "   EOS token: '$EOS_TOKEN' = $eosTokenId")
            Log.i(TAG, "   IM_START token: '$IM_START_TOKEN' = $imStartTokenId")
            Log.i(TAG, "   IM_END token: '$IM_END_TOKEN' = $imEndTokenId")

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
     * Initialize byte encoder (GPT-2/Qwen's exact byte-to-unicode mapping)
     */
    private fun initByteEncoder() {
        val bs = mutableListOf<Int>()

        // Add printable ASCII (except space)
        bs.addAll(('!'.code..'~'.code))           // 33-126

        // Add Latin-1 Supplement characters
        bs.addAll(('¡'.code..'¬'.code))           // 161-172
        bs.addAll(('®'.code..'ÿ'.code))           // 174-255

        val cs = bs.toMutableList()
        var n = 0

        // For bytes not in bs, map to higher Unicode code points
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }

        // Create the mapping dictionaries
        for (i in bs.indices) {
            val byteVal = bs[i]
            val charVal = cs[i].toChar().toString()
            byteEncoder[byteVal] = charVal
            byteDecoder[charVal] = byteVal
        }

        Log.d(TAG, "   Byte encoder created: ${byteEncoder.size} mappings")
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
     *
     * @param text Input text to encode
     * @param addImTokens Whether to add <|im_start|> and <|im_end|> tokens for chat format
     */
    fun encode(text: String, addImTokens: Boolean = true): LongArray {
        Log.i(TAG, "🔤 BPE Encoding text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")

        val tokens = mutableListOf<Long>()

        // Add IM_START token if using chat format
        if (addImTokens) {
            tokens.add(imStartTokenId.toLong())
        }

        // Qwen pattern: similar to GPT-2 but handles Chinese characters
        val pattern = Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")
        val words = pattern.findAll(text).map { it.value }.toList()

        Log.i(TAG, "   Regex split into ${words.size} words: ${words.take(5)}")

        for (word in words) {
            Log.d(TAG, "   Processing word: '$word'")

            // Convert word to bytes, then to unicode chars using byte encoder
            val tokenBytes = word.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "     Bytes: ${tokenBytes.take(10).map { it.toInt() and 0xFF }}")

            val encodedWord = tokenBytes.map {
                val byte = it.toInt() and 0xFF
                val encoded = byteEncoder[byte]
                if (encoded == null) {
                    Log.w(TAG, "     ⚠️  Missing byte encoder for byte $byte")
                }
                encoded ?: ""
            }.joinToString("")

            Log.d(TAG, "     Byte-encoded: '$encodedWord'")

            // Apply BPE to get the final token representation
            val bpeResult = bpe(encodedWord)
            Log.d(TAG, "     BPE result: '$bpeResult'")

            val bpeTokens = bpeResult.split(" ")
            Log.d(TAG, "     BPE tokens: $bpeTokens")

            for (bpeToken in bpeTokens) {
                val tokenId = vocab[bpeToken]
                if (tokenId != null) {
                    tokens.add(tokenId.toLong())
                    Log.d(TAG, "       Token '$bpeToken' → ID $tokenId")
                } else {
                    Log.w(TAG, "       ⚠️  Unknown BPE token: '$bpeToken'")
                }
            }
        }

        // Add IM_END token if using chat format
        if (addImTokens) {
            tokens.add(imEndTokenId.toLong())
        }

        Log.i(TAG, "✅ BPE encoded to ${tokens.size} tokens")
        Log.i(TAG, "   Token IDs: ${tokens.joinToString()}")

        return tokens.toLongArray()
    }

    /**
     * Decode token IDs to text
     */
    fun decode(ids: LongArray): String {
        Log.d(TAG, "🔠 Decoding ${ids.size} token IDs...")

        val previewIds = if (ids.size > 20) {
            ids.sliceArray(0 until 20).joinToString()
        } else {
            ids.joinToString()
        }
        Log.d(TAG, "   Token IDs: $previewIds${if (ids.size > 20) "..." else ""}")

        var unknownCount = 0
        val tokens = mutableListOf<String>()

        for (id in ids) {
            // Skip special tokens in output
            if (id.toInt() == imStartTokenId || id.toInt() == imEndTokenId || id.toInt() == eosTokenId) {
                continue
            }

            val token = reverseVocab[id.toInt()]
            if (token == null) {
                unknownCount++
                Log.w(TAG, "⚠️  Unknown token ID: $id")
            } else {
                tokens.add(token)
            }
        }

        // Decode bytes back to text
        val byteList = mutableListOf<Byte>()
        for (token in tokens) {
            for (char in token) {
                val charStr = char.toString()
                val byte = byteDecoder[charStr]
                if (byte != null) {
                    byteList.add(byte.toByte())
                }
            }
        }

        val text = byteList.toByteArray().toString(Charsets.UTF_8)

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
     * Clean up resources
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
