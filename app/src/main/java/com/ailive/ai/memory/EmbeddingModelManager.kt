package com.ailive.ai.memory

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ailive.ai.llm.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

/**
 * EmbeddingModelManager - ONNX Runtime-based semantic embeddings
 *
 * Uses BGE-small-en-v1.5 ONNX model for generating 384-dimensional embeddings.
 * Replaces deterministic random embeddings with real semantic vectors.
 *
 * Features:
 * - ONNX Runtime inference (optimized for mobile)
 * - WordPiece tokenization from tokenizer.json
 * - 384-dimensional output vectors
 * - Mean pooling over token embeddings
 * - Inference time: < 50ms per text
 *
 * @author AILive Memory System Team
 * @since v1.4 - Phase 2: Real Embeddings
 */
class EmbeddingModelManager(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddingModelManager"
        private const val EMBEDDING_DIM = 384  // BGE-small-en-v1.5 output dimension
        private const val MAX_SEQUENCE_LENGTH = 512  // BERT-style max length
        private const val CLS_TOKEN_ID = 101L  // [CLS] token
        private const val SEP_TOKEN_ID = 102L  // [SEP] token
        private const val PAD_TOKEN_ID = 0L    // [PAD] token
    }

    private val modelDownloadManager = ModelDownloadManager(context)

    // ONNX Runtime components
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Tokenizer vocabulary (loaded from tokenizer.json)
    private var vocabulary: Map<String, Long> = emptyMap()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    private var initializationError: String? = null

    /**
     * Initialize embedding model (BGE-small-en-v1.5 ONNX)
     * Call on app startup in background thread
     *
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.i(TAG, "Embedding model already initialized")
            return@withContext true
        }

        if (isInitializing) {
            Log.w(TAG, "Embedding model initialization already in progress")
            return@withContext false
        }

        isInitializing = true
        initializationError = null

        try {
            Log.i(TAG, "🔢 Initializing Embedding Model (BGE-small-en-v1.5)...")
            Log.i(TAG, "   Purpose: Semantic embeddings for memory retrieval")

            // Check if BGE model is available
            if (!modelDownloadManager.isBGEModelAvailable()) {
                val error = "BGE model not found. Using fallback random embeddings."
                Log.w(TAG, "⚠️  $error")
                Log.i(TAG, "   Required files: model_quantized.onnx, tokenizer.json, config.json")
                Log.i(TAG, "   Download from model selection dialog")
                Log.i(TAG, "   App will continue with deterministic random embeddings")
                initializationError = error
                isInitializing = false
                return@withContext false  // Non-critical - app can run without it
            }

            // Get model paths
            val modelPath = modelDownloadManager.getModelPath(ModelDownloadManager.BGE_MODEL_ONNX)
            val tokenizerPath = modelDownloadManager.getModelPath(ModelDownloadManager.BGE_TOKENIZER_JSON)
            val configPath = modelDownloadManager.getModelPath(ModelDownloadManager.BGE_CONFIG_JSON)

            Log.i(TAG, "📂 Loading BGE model files:")
            Log.i(TAG, "   Model: ${File(modelPath).name} (${File(modelPath).length() / 1024 / 1024}MB)")
            Log.i(TAG, "   Tokenizer: ${File(tokenizerPath).name}")
            Log.i(TAG, "   Config: ${File(configPath).name}")

            // Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "✓ ONNX Runtime environment created")

            // Create ONNX session with mobile-optimized settings
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Use all available CPU threads for inference
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
                setInterOpNumThreads(1)  // Single op at a time for mobile
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

                // Mobile-specific optimizations
                addConfigEntry("session.intra_op.allow_spinning", "0")  // Don't busy-wait
                addConfigEntry("session.inter_op.allow_spinning", "0")
            }

            ortSession = ortEnvironment!!.createSession(modelPath, sessionOptions)
            Log.d(TAG, "✓ ONNX session created with optimizations")

            // Load tokenizer vocabulary
            loadTokenizer(tokenizerPath)

            isInitialized = true
            isInitializing = false

            Log.i(TAG, "✅ Embedding Model initialized successfully!")
            Log.i(TAG, "   Model: BGE-small-en-v1.5 (ONNX INT8 quantized)")
            Log.i(TAG, "   Output: ${EMBEDDING_DIM}-dimensional vectors")
            Log.i(TAG, "   Vocabulary: ${vocabulary.size} tokens")
            Log.i(TAG, "   Expected performance: < 50ms per inference")
            Log.i(TAG, "🔢 Semantic embeddings ready!")

            true
        } catch (e: Exception) {
            val error = "Embedding model initialization failed: ${e.message}"
            Log.e(TAG, "❌ Failed to initialize embedding model", e)
            Log.w(TAG, "   App will use fallback random embeddings")
            e.printStackTrace()
            initializationError = error
            isInitializing = false

            // Clean up on failure
            cleanup()

            false  // Non-fatal - app continues with random embeddings
        }
    }

    /**
     * Generate semantic embedding for text
     *
     * @param text Text to embed (any length, will be truncated to MAX_SEQUENCE_LENGTH)
     * @return 384-dimensional embedding vector, or null if model not initialized
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Embedding model not initialized - cannot generate embeddings")
            return@withContext null
        }

        if (text.isBlank()) {
            Log.d(TAG, "Empty text - returning zero vector")
            return@withContext FloatArray(EMBEDDING_DIM) { 0f }
        }

        try {
            // Tokenize text
            val tokens = tokenize(text)

            // Create ONNX tensor inputs
            val inputIds = LongBuffer.wrap(tokens)
            val attentionMask = LongBuffer.wrap(LongArray(tokens.size) { 1L })  // All tokens are attended to
            val tokenTypeIds = LongBuffer.wrap(LongArray(tokens.size) { 0L })  // All tokens from same sequence

            val inputIdsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                inputIds,
                longArrayOf(1, tokens.size.toLong())  // Shape: [batch_size=1, seq_length]
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                ortEnvironment,
                attentionMask,
                longArrayOf(1, tokens.size.toLong())
            )
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                tokenTypeIds,
                longArrayOf(1, tokens.size.toLong())
            )

            // Run inference
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val outputs = ortSession!!.run(inputs)

            // Extract embeddings from output
            // BGE model outputs: [batch_size, seq_length, hidden_size]
            // We take mean pooling over sequence length to get [batch_size, hidden_size]
            val lastHiddenState = outputs[0].value as Array<Array<FloatArray>>
            val embedding = meanPooling(lastHiddenState[0], tokens)

            // Normalize embedding (L2 normalization for cosine similarity)
            val normalizedEmbedding = normalize(embedding)

            // Clean up tensors
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            outputs.close()

            Log.d(TAG, "✓ Generated embedding for text: ${text.take(50)}...")

            normalizedEmbedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding: ${e.message}", e)
            null
        }
    }

    /**
     * Batch generate embeddings for multiple texts (more efficient)
     *
     * @param texts List of texts to embed
     * @return List of embeddings (same order as input), nulls for failed generations
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Embedding model not initialized - cannot generate embeddings")
            return@withContext List(texts.size) { null }
        }

        // For now, process sequentially (batch inference requires more complex tensor management)
        // TODO: Implement true batch inference for better performance
        texts.map { text -> embed(text) }
    }

    /**
     * Check if embedding model is ready for use
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get initialization error message (if any)
     */
    fun getError(): String? = initializationError

    /**
     * Clean up ONNX Runtime resources
     */
    fun cleanup() {
        try {
            ortSession?.close()
            ortSession = null

            // Note: Don't close ortEnvironment - it's a singleton
            ortEnvironment = null

            isInitialized = false
            Log.i(TAG, "🧹 Embedding model resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    // ===== PRIVATE HELPER METHODS =====

    /**
     * Load tokenizer vocabulary from tokenizer.json
     */
    private fun loadTokenizer(tokenizerPath: String) {
        try {
            val tokenizerJson = File(tokenizerPath).readText()
            val tokenizerData = JSONObject(tokenizerJson)

            // Extract vocabulary from tokenizer.json
            // Format: {"model": {"vocab": {"token": id, ...}}}
            val vocabJson = tokenizerData.getJSONObject("model").getJSONObject("vocab")

            val vocabMap = mutableMapOf<String, Long>()
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabJson.getLong(token)
                vocabMap[token] = id
            }

            vocabulary = vocabMap
            Log.d(TAG, "✓ Loaded tokenizer vocabulary: ${vocabulary.size} tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tokenizer vocabulary", e)
            throw e
        }
    }

    /**
     * Tokenize text using WordPiece tokenization
     *
     * Simplified implementation - uses greedy longest-match tokenization
     */
    private fun tokenize(text: String): LongArray {
        val tokens = mutableListOf<Long>()

        // Add [CLS] token at start
        tokens.add(CLS_TOKEN_ID)

        // Basic preprocessing: lowercase and split on whitespace/punctuation
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")  // Remove punctuation
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // Tokenize each word
        for (word in words) {
            var remaining = word

            while (remaining.isNotEmpty()) {
                var found = false

                // Try to find longest matching subword
                for (length in remaining.length downTo 1) {
                    val subword = if (remaining == word) {
                        remaining.substring(0, length)
                    } else {
                        "##${remaining.substring(0, length)}"  // WordPiece continuation
                    }

                    val tokenId = vocabulary[subword]
                    if (tokenId != null) {
                        tokens.add(tokenId)
                        remaining = remaining.substring(length)
                        found = true
                        break
                    }
                }

                if (!found) {
                    // Unknown token - use [UNK] token (usually ID 100)
                    tokens.add(100L)
                    remaining = remaining.substring(1)
                }
            }
        }

        // Add [SEP] token at end
        tokens.add(SEP_TOKEN_ID)

        // Truncate to max sequence length
        val truncated = if (tokens.size > MAX_SEQUENCE_LENGTH) {
            tokens.subList(0, MAX_SEQUENCE_LENGTH - 1).toMutableList().apply {
                add(SEP_TOKEN_ID)  // Ensure [SEP] at end
            }
        } else {
            tokens
        }

        return truncated.toLongArray()
    }

    /**
     * Mean pooling over token embeddings
     * Averages embeddings for all non-padding tokens
     */
    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, tokens: LongArray): FloatArray {
        val result = FloatArray(EMBEDDING_DIM) { 0f }
        var count = 0

        // Average embeddings for non-padding tokens
        for (i in tokens.indices) {
            if (tokens[i] != PAD_TOKEN_ID) {
                for (j in 0 until EMBEDDING_DIM) {
                    result[j] += tokenEmbeddings[i][j]
                }
                count++
            }
        }

        // Divide by count to get mean
        if (count > 0) {
            for (j in 0 until EMBEDDING_DIM) {
                result[j] /= count
            }
        }

        return result
    }

    /**
     * L2 normalize embedding vector
     * Required for cosine similarity comparisons
     */
    private fun normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)

        if (norm > 0f) {
            return FloatArray(embedding.size) { i -> embedding[i] / norm }
        }
        return embedding
    }
}
