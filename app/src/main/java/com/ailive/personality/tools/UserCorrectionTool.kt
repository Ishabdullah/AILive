package com.ailive.personality.tools

import android.content.Context
import android.util.Log
import com.ailive.memory.managers.UnifiedMemoryManager
import com.ailive.memory.database.entities.FactCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * User Correction Tool - Learn from user feedback and corrections
 *
 * This tool allows users to teach AILive when it makes mistakes or gives wrong answers.
 * Corrections are stored in long-term memory so AILive learns and improves.
 *
 * Use cases:
 * - User says: "That's wrong"
 * - User says: "You need to use GPS to find my location"
 * - User says: "Search the web for that"
 * - User provides better answer or correction
 * - User teaches better approaches
 *
 * Natural language triggers:
 * - "that's wrong" / "that's incorrect" / "no, that's not right"
 * - "you should use [tool/method]"
 * - "try using [capability]"
 * - "the correct answer is..."
 * - "actually, ..." / "in fact, ..."
 *
 * Corrections are stored as:
 * - What went wrong
 * - What should have been done
 * - When it happened
 * - Context of the mistake
 *
 * @param context Android context
 * @param memoryManager Unified memory system for storing corrections
 * @since v1.4 - User Correction System
 */
class UserCorrectionTool(
    private val context: Context,
    private val memoryManager: UnifiedMemoryManager?
) : BaseTool() {

    override val name: String = "record_correction"

    override val description: String = """
        Record user corrections and feedback to improve AILive's responses.

        AUTOMATIC DETECTION - This tool is called when user:
        - Says "that's wrong" / "that's incorrect" / "no"
        - Says "you should use [tool]" / "try [method]"
        - Says "actually..." / "in fact..." / "the correct answer is..."
        - Provides correction after a mistake
        - Teaches better approach

        Parameters:
        - correction_type: "wrong_answer" | "wrong_tool" | "missed_capability" | "other"
        - what_went_wrong: Description of the mistake
        - correct_approach: What should have been done
        - user_message: The original correction message from user

        Example:
        User: "What town am I in?"
        AILive: "I'm sorry, but I'm not able to assist with that."
        User: "That's wrong. You need to use your GPS tools to find out."

        → record_correction called:
        {
          "correction_type": "wrong_tool",
          "what_went_wrong": "Did not use location/GPS tool when asked about current location",
          "correct_approach": "Use get_location tool to find current city/town",
          "user_message": "That's wrong. You need to use your GPS tools to find out."
        }
    """.trimIndent()

    override val requiresPermissions: Boolean = false

    private val TAG = "UserCorrectionTool"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Training data file for fine-tuning Qwen
    private val trainingFile by lazy {
        val trainingDir = java.io.File(context.filesDir, "training")
        if (!trainingDir.exists()) {
            trainingDir.mkdirs()
        }
        java.io.File(trainingDir, "qwen_corrections_training.jsonl")
    }

    /**
     * Record a user correction
     *
     * Parameters:
     * - correction_type: Type of correction
     * - what_went_wrong: What the mistake was
     * - correct_approach: What should have been done
     * - user_message: Original user correction message
     * - context_query: The original query that led to the mistake (optional)
     * - ai_response: AILive's incorrect response (optional)
     */
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            val correctionType = parameters["correction_type"] as? String ?: "other"
            val whatWentWrong = parameters["what_went_wrong"] as? String ?: "No description"
            val correctApproach = parameters["correct_approach"] as? String ?: "No approach specified"
            val userMessage = parameters["user_message"] as? String ?: ""
            val contextQuery = parameters["context_query"] as? String
            val aiResponse = parameters["ai_response"] as? String

            Log.i(TAG, "📝 Recording user correction:")
            Log.i(TAG, "   Type: $correctionType")
            Log.i(TAG, "   What went wrong: $whatWentWrong")
            Log.i(TAG, "   Correct approach: $correctApproach")

            // Format correction as a structured fact
            val correctionFact = buildString {
                append("USER CORRECTION (${getCurrentTimestamp()}):\n\n")
                append("TYPE: $correctionType\n\n")
                append("WHAT WENT WRONG:\n$whatWentWrong\n\n")
                append("CORRECT APPROACH:\n$correctApproach\n\n")
                if (contextQuery != null) {
                    append("ORIGINAL QUERY: $contextQuery\n\n")
                }
                if (aiResponse != null) {
                    append("INCORRECT RESPONSE: $aiResponse\n\n")
                }
                append("USER FEEDBACK: $userMessage\n")
            }

            // Store in memory system
            var memoryStored = false
            if (memoryManager != null) {
                scope.launch {
                    try {
                        // Store as a high-importance behavioral fact
                        memoryManager.longTermMemory.learnFact(
                            category = FactCategory.BEHAVIORAL,
                            fact = correctionFact,
                            conversationId = null,
                            importance = 0.9f  // High importance - user-provided teaching
                        )

                        Log.i(TAG, "✅ Correction saved to long-term memory")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save correction to memory", e)
                    }
                }
                memoryStored = true
            } else {
                Log.w(TAG, "⚠️ Memory system not available - correction logged but not persisted in memory")
            }

            // ALWAYS save to training file for Qwen fine-tuning
            val trainingSaved = saveToTrainingFile(
                correctionType = correctionType,
                contextQuery = contextQuery,
                incorrectResponse = aiResponse,
                correctApproach = correctApproach,
                userCorrection = userMessage
            )

            if (trainingSaved) {
                Log.i(TAG, "✅ Correction saved to training file: ${trainingFile.absolutePath}")
                Log.i(TAG, "   Total training examples: ${countTrainingExamples()}")
            }

            return ToolResult.success(
                data = mapOf(
                    "correction_recorded" to true,
                    "correction_type" to correctionType,
                    "stored_in_memory" to memoryStored,
                    "saved_to_training" to trainingSaved,
                    "training_file" to trainingFile.absolutePath,
                    "total_training_examples" to countTrainingExamples()
                ),
                message = "Thank you for the correction. I've recorded this feedback and will learn from it."
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to record correction", e)
            return ToolResult.error("Failed to record correction: ${e.message}")
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return dateFormat.format(Date())
    }

    /**
     * Save correction to JSONL training file for Qwen fine-tuning
     *
     * Format: Each line is a JSON object with instruction-response pairs
     * Compatible with standard fine-tuning formats (Alpaca, ShareGPT, etc.)
     */
    private fun saveToTrainingFile(
        correctionType: String,
        contextQuery: String?,
        incorrectResponse: String?,
        correctApproach: String,
        userCorrection: String
    ): Boolean {
        return try {
            // Create training example in Alpaca/Instruction format
            val trainingExample = org.json.JSONObject().apply {
                put("timestamp", getCurrentTimestamp())
                put("correction_type", correctionType)

                // Instruction: What the user asked
                if (contextQuery != null) {
                    put("instruction", contextQuery)
                }

                // Input: What AILive did wrong
                if (incorrectResponse != null) {
                    put("incorrect_output", incorrectResponse)
                }

                // Output: What AILive should have done
                put("correct_approach", correctApproach)
                put("user_feedback", userCorrection)

                // Create a training pair format for fine-tuning
                val messages = org.json.JSONArray()

                // System message (describes tools and capabilities)
                messages.put(org.json.JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an AI assistant with access to tools: get_location (GPS), web_search, retrieve_memory, and others. ALWAYS use appropriate tools to answer questions.")
                })

                // User message
                if (contextQuery != null) {
                    messages.put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", contextQuery)
                    })
                }

                // Assistant message (correct approach)
                messages.put(org.json.JSONObject().apply {
                    put("role", "assistant")
                    put("content", correctApproach)
                })

                put("messages", messages)

                // Add metadata for analysis
                put("correction_metadata", org.json.JSONObject().apply {
                    put("what_went_wrong", if (incorrectResponse != null) "Gave incorrect response: $incorrectResponse" else "Did not use appropriate tool")
                    put("correction_category", correctionType)
                })
            }

            // Append to JSONL file (one JSON object per line)
            trainingFile.appendText(trainingExample.toString() + "\n")

            Log.d(TAG, "Training example saved: $correctionType")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save training example", e)
            false
        }
    }

    /**
     * Count total training examples in file
     */
    private fun countTrainingExamples(): Int {
        return try {
            if (!trainingFile.exists()) {
                0
            } else {
                trainingFile.readLines().count { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count training examples", e)
            0
        }
    }

    /**
     * Export training file path for fine-tuning
     * Call this to get the file for fine-tuning Qwen
     */
    fun getTrainingFilePath(): String {
        return trainingFile.absolutePath
    }

    /**
     * Clear training file (use with caution!)
     */
    fun clearTrainingData() {
        try {
            if (trainingFile.exists()) {
                trainingFile.delete()
                Log.i(TAG, "Training data cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear training data", e)
        }
    }
}
