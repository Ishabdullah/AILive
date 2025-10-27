package com.ailive.memory.embeddings

import android.util.Log
import kotlin.random.Random

/**
 * Text embedding generator for AILive.
 * 
 * PLACEHOLDER: Uses deterministic random embeddings for now.
 * TODO: Integrate actual BGE-small-en-v1.5 model via ONNX Runtime.
 * 
 * Model: BAAI/bge-small-en-v1.5
 * License: MIT
 * Dimensions: 384
 * Size: 133 MB
 * Commercial Use: YES - Fully permitted
 */
class TextEmbedder(private val dimensions: Int = 384) {
    private val TAG = "TextEmbedder"
    
    /**
     * Generate embedding for text.
     * Currently returns deterministic random based on text hash.
     * 
     * When integrated, will use BGE-small-en-v1.5:
     * - Better quality than MiniLM-L6-v2
     * - MIT license (100% commercial safe)
     * - Training data all commercially licensed
     */
    fun embed(text: String): FloatArray {
        // Use text hash as seed for reproducibility
        val seed = text.hashCode().toLong()
        val random = Random(seed)
        
        // Generate random embedding (normalized)
        val embedding = FloatArray(dimensions) {
            random.nextFloat() - 0.5f
        }
        
        return normalize(embedding)
    }
    
    /**
     * Batch embed multiple texts.
     */
    fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { embed(it) }
    }
    
    /**
     * Normalize vector to unit length.
     */
    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in vector) {
            sumSquares += value * value
        }
        val norm = kotlin.math.sqrt(sumSquares)
        
        return if (norm > 0f) {
            FloatArray(vector.size) { i -> vector[i] / norm }
        } else {
            vector
        }
    }
    
    companion object {
        /**
         * Recommended dimensions for different models.
         */
        const val BGE_SMALL_EN_DIMS = 384      // BAAI/bge-small-en-v1.5 (MIT License)
        const val BGE_BASE_EN_DIMS = 768       // BAAI/bge-base-en-v1.5 (MIT License)
        const val E5_SMALL_V2_DIMS = 384       // E5-small-v2 (MIT License)
        const val OPENAI_DIMS = 1536           // OpenAI embeddings (proprietary)
    }
}
