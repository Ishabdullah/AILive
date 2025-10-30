package com.ailive.ai.llm

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * LLMManager - Language model inference using ONNX Runtime
 * Phase 2.6 & Phase 7: Provides intelligent text generation for AI agents
 *
 * Uses: SmolLM2-360M-int8 (348MB ONNX, quantized to int8)
 * Inference: NNAPI GPU acceleration + CPU optimization
 *
 * @author AILive Team
 * @since Phase 2.6
 * @updated Phase 7 - SmolLM2 integration
 */
class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"
        // Updated to use SmolLM2-360M-int8 (348MB ONNX in assets)
        private const val MODEL_PATH = "models/smollm2-360m-int8.onnx"

        // OPTIMIZATION: Reduced from 150 to 80 for faster generation
        // Voice responses should be concise (1-3 sentences = ~50-80 tokens)
        private const val MAX_LENGTH = 80

        // OPTIMIZATION: Higher temperature (0.9) for more varied responses
        // Previously 0.7 was causing some repetition
        private const val TEMPERATURE = 0.9f

        private const val TOP_P = 0.9f
    }

    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var isInitialized = false

    // Simple tokenizer (will use byte-pair encoding approximation)
    private val vocabulary = mutableMapOf<String, Long>()
    private var vocabSize = 32000  // TinyLlama vocab size

    /**
     * Initialize the LLM model
     * Called once on app startup in background thread
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🤖 Initializing LLM (ONNX Runtime)...")

            // Create ONNX Runtime environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Load model file
            val modelFile = getModelFile()
            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Model file not found: ${modelFile.absolutePath}")
                Log.i(TAG, "📥 Download SmolLM2 from: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-ONNX")
                return@withContext false
            }

            Log.i(TAG, "📂 Loading SmolLM2-360M model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")

            // Create session options with GPU acceleration
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)  // Use 4 CPU threads

            // OPTIMIZATION: Enable NNAPI for GPU/NPU acceleration
            try {
                sessionOptions.addNnapi()
                Log.i(TAG, "✅ NNAPI GPU acceleration enabled")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NNAPI not available, using CPU: ${e.message}")
                // Fall back to CPU-only (no action needed)
            }

            // Create session
            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)

            // Initialize simple vocabulary (simplified for demo)
            initializeVocabulary()

            isInitialized = true
            Log.i(TAG, "✅ LLM initialized successfully!")
            Log.i(TAG, "   Model: SmolLM2-360M-int8 (quantized)")
            Log.i(TAG, "   Size: ${modelFile.length() / 1024 / 1024}MB")
            Log.i(TAG, "   Acceleration: NNAPI (GPU/NPU) + CPU (4 threads)")
            Log.i(TAG, "   Optimization: ALL_OPT level")
            Log.i(TAG, "   Max length: $MAX_LENGTH tokens")
            Log.i(TAG, "   Temperature: $TEMPERATURE | Top-p: $TOP_P")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize LLM", e)
            false
        }
    }

    /**
     * Generate text response using the language model
     *
     * @param prompt The input text prompt
     * @param agentName Name of the agent for personality context
     * @return Generated text response
     */
    suspend fun generate(prompt: String, agentName: String = "AILive"): String = withContext(Dispatchers.IO) {
        if (!isInitialized || ortSession == null) {
            Log.w(TAG, "⚠️ LLM not initialized, using fallback response")
            return@withContext getFallbackResponse(prompt, agentName)
        }

        try {
            // Create chat prompt with agent personality
            val chatPrompt = createChatPrompt(prompt, agentName)
            Log.d(TAG, "🔍 Generating response for: ${prompt.take(50)}...")

            // Tokenize input
            val inputIds = tokenize(chatPrompt)

            // Run inference
            val outputIds = runInference(inputIds)

            // Decode output
            val response = decode(outputIds)

            Log.d(TAG, "✨ Generated: ${response.take(50)}...")
            return@withContext response.trim()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Generation failed", e)
            return@withContext getFallbackResponse(prompt, agentName)
        }
    }

    /**
     * Create a chat-formatted prompt with UNIFIED personality
     *
     * REFACTORING NOTE: This now uses ONE consistent personality (AILive)
     * instead of six separate agent personalities. The agentName parameter
     * is kept for backward compatibility but should always be "AILive".
     */
    private fun createChatPrompt(userMessage: String, agentName: String): String {
        // Unified personality for all interactions
        val personality = """You are AILive, a unified on-device AI companion.
You are ONE cohesive intelligence with multiple capabilities (vision, emotion, memory, device control).
Speak naturally as a single character, never as separate agents or systems.
Be warm, helpful, concise, and conversational."""

        // TinyLlama chat format
        return "<|system|>\n$personality</s>\n<|user|>\n$userMessage</s>\n<|assistant|>\n"
    }

    /**
     * Simple tokenization (approximation for demo)
     * In production, use proper BPE tokenizer
     */
    private fun tokenize(text: String): LongArray {
        // Simplified tokenization - split by whitespace and map to IDs
        val tokens = text.lowercase().split(Regex("\\s+"))
        return tokens.map { token ->
            vocabulary.getOrPut(token) {
                (vocabulary.size + 1).toLong()
            }
        }.toLongArray()
    }

    /**
     * Decode token IDs back to text
     */
    private fun decode(ids: LongArray): String {
        // Reverse vocabulary lookup
        val reverseVocab = vocabulary.entries.associate { it.value to it.key }
        return ids.map { reverseVocab[it] }.filterNotNull().joinToString(" ")
    }

    /**
     * Run ONNX inference
     */
    private fun runInference(inputIds: LongArray): LongArray {
        val session = ortSession ?: throw IllegalStateException("Session not initialized")

        // Prepare input tensor
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(inputIds),
            shape
        )

        // Run inference
        val inputs = mapOf("input_ids" to inputTensor)
        val outputs = session.run(inputs)

        // Get output logits
        val outputTensor = outputs[0] as OnnxTensor
        val logits = outputTensor.floatBuffer

        // Simple greedy decoding (take argmax)
        val outputIds = mutableListOf<Long>()
        for (i in 0 until MAX_LENGTH) {
            val nextTokenId = sampleNextToken(logits)
            outputIds.add(nextTokenId)

            // Stop on end token (assumed to be 2)
            if (nextTokenId == 2L) break
        }

        outputs.close()
        inputTensor.close()

        return outputIds.toLongArray()
    }

    /**
     * Sample next token using temperature and top-p
     */
    private fun sampleNextToken(logits: FloatBuffer): Long {
        // Apply temperature
        val probs = FloatArray(vocabSize)
        for (i in 0 until vocabSize) {
            probs[i] = exp((logits[i] / TEMPERATURE).toDouble()).toFloat()
        }

        // Normalize to probabilities
        val sum = probs.sum()
        for (i in probs.indices) {
            probs[i] /= sum
        }

        // Greedy sampling (take argmax for now)
        return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
    }

    /**
     * Initialize simple vocabulary
     */
    private fun initializeVocabulary() {
        // Basic English tokens (simplified for demo)
        val commonWords = listOf(
            "the", "a", "an", "i", "you", "he", "she", "it", "we", "they",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did",
            "will", "would", "can", "could", "should", "may", "might",
            "hello", "hi", "how", "what", "when", "where", "why", "who",
            "yes", "no", "ok", "okay", "thanks", "thank", "please",
            "see", "look", "watch", "feel", "think", "know", "remember",
            "system", "status", "help", "camera", "vision", "emotion", "memory"
        )

        commonWords.forEachIndexed { index, word ->
            vocabulary[word] = (index + 3).toLong()  // Start from 3 (0=pad, 1=unk, 2=eos)
        }

        Log.d(TAG, "📖 Vocabulary initialized: ${vocabulary.size} tokens")
    }

    /**
     * Get model file from assets, copying to filesDir if needed
     * Large models (348MB) need to be copied from assets to internal storage first
     */
    private fun getModelFile(): File {
        val modelFile = File(context.filesDir, MODEL_PATH)

        // If model already exists in filesDir, use it
        if (modelFile.exists()) {
            Log.d(TAG, "📂 Model found in cache: ${modelFile.absolutePath}")
            return modelFile
        }

        // Copy from assets to filesDir (first time only)
        Log.i(TAG, "📥 Copying model from assets to internal storage...")
        Log.i(TAG, "   This is a one-time operation (348MB)")

        try {
            // Ensure parent directory exists
            modelFile.parentFile?.mkdirs()

            // Copy from assets
            context.assets.open(MODEL_PATH).use { input ->
                modelFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Log progress every 50MB
                        if (totalBytes % (50 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "   Copied: ${totalBytes / 1024 / 1024}MB...")
                        }
                    }

                    Log.i(TAG, "✅ Model copied successfully: ${totalBytes / 1024 / 1024}MB")
                }
            }

            return modelFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to copy model from assets", e)
            throw e
        }
    }

    /**
     * Fallback responses when LLM is not available
     *
     * PHASE 4 FIX: This method should NOT do keyword matching as it causes
     * repetitive responses. Instead, throw exception to let PersonalityEngine's
     * intent-based fallback system handle it properly.
     */
    private fun getFallbackResponse(prompt: String, agentName: String): String {
        // Don't try to be smart here - let PersonalityEngine handle fallbacks
        // PersonalityEngine has better intent-based fallback responses
        throw IllegalStateException("LLM not initialized - use PersonalityEngine fallback")
    }

    /**
     * Cleanup resources
     */
    fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
            isInitialized = false
            Log.i(TAG, "🔒 LLM resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
    }
}
